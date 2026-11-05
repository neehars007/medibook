package com.coolapp.medibook.patient;

import com.coolapp.medibook.audit.AccessLog;
import com.coolapp.medibook.audit.AccessLogRepository;
import com.coolapp.medibook.audit.AccessResource;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.auth.User;
import com.coolapp.medibook.auth.UserRepository;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.patient.PatientDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patients;
    private final MedicalHistoryRepository history;
    private final PatientAccessService access;
    private final AccessLogRepository accessLogs;
    private final UserRepository users;
    private final DoctorRepository doctors;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PatientDto me() {
        return PatientDto.from(access.myPatient());
    }

    @Transactional
    public PatientDto updateMe(PatientUpdateRequest req) {
        Patient p = access.myPatient();
        apply(p, req);
        audit.log("PATIENT_UPDATED", "Patient", p.getId(), null);
        return PatientDto.from(p);
    }

    // ---------- Dependents ----------

    @Transactional(readOnly = true)
    public List<PatientDto> dependents() {
        return patients.findByGuardianPatientId(access.myPatient().getId()).stream().map(PatientDto::from).toList();
    }

    @Transactional
    public PatientDto addDependent(PatientUpdateRequest req) {
        Patient guardian = access.myPatient();
        Patient d = new Patient();
        d.setGuardianPatientId(guardian.getId());
        apply(d, req);
        if (d.getPhone() == null) {
            d.setPhone(guardian.getPhone());
        }
        patients.save(d);
        audit.log("DEPENDENT_ADDED", "Patient", d.getId(), null);
        return PatientDto.from(d);
    }

    @Transactional
    public PatientDto updateDependent(UUID id, PatientUpdateRequest req) {
        Patient d = requireDependent(id);
        apply(d, req);
        audit.log("DEPENDENT_UPDATED", "Patient", id, null);
        return PatientDto.from(d);
    }

    @Transactional
    public void removeDependent(UUID id) {
        Patient d = requireDependent(id);
        d.setDeleted(true); // soft delete: medical data is never hard-deleted
        audit.log("DEPENDENT_REMOVED", "Patient", id, null);
    }

    private Patient requireDependent(UUID id) {
        Patient guardian = access.myPatient();
        Patient d = access.load(id);
        if (!guardian.getId().equals(d.getGuardianPatientId())) {
            throw ApiException.forbidden("Not your dependent");
        }
        return d;
    }

    // ---------- Medical history ----------

    @Transactional
    public List<MedicalHistoryDto> history(UUID patientId) {
        access.requireClinicalAccess(patientId, AccessResource.HISTORY, null);
        return history.findByPatientIdOrderByTypeAscCreatedAtDesc(patientId).stream().map(MedicalHistoryDto::from).toList();
    }

    @Transactional
    public MedicalHistoryDto addHistory(UUID patientId, MedicalHistoryRequest req) {
        access.requireHistoryEdit(patientId);
        MedicalHistory h = new MedicalHistory();
        h.setPatientId(patientId);
        h.setType(req.type());
        h.setDescription(req.description());
        h.setSince(req.since());
        h.setActive(req.active() == null || req.active());
        history.save(h);
        audit.log("HISTORY_ADDED", "MedicalHistory", h.getId(), h.getType().name());
        return MedicalHistoryDto.from(h);
    }

    @Transactional
    public MedicalHistoryDto updateHistory(UUID patientId, UUID historyId, MedicalHistoryRequest req) {
        access.requireHistoryEdit(patientId);
        MedicalHistory h = loadHistory(patientId, historyId);
        h.setType(req.type());
        h.setDescription(req.description());
        h.setSince(req.since());
        if (req.active() != null) {
            h.setActive(req.active());
        }
        audit.log("HISTORY_UPDATED", "MedicalHistory", historyId, null);
        return MedicalHistoryDto.from(h);
    }

    @Transactional
    public void deleteHistory(UUID patientId, UUID historyId) {
        access.requireHistoryEdit(patientId);
        loadHistory(patientId, historyId).setDeleted(true);
        audit.log("HISTORY_DELETED", "MedicalHistory", historyId, null);
    }

    private MedicalHistory loadHistory(UUID patientId, UUID historyId) {
        MedicalHistory h = history.findById(historyId).orElseThrow(() -> ApiException.notFound("History entry"));
        if (!h.getPatientId().equals(patientId)) {
            throw ApiException.notFound("History entry");
        }
        return h;
    }

    // ---------- "Who viewed my records" ----------

    @Transactional(readOnly = true)
    public List<AccessLogDto> myAccessLog(int page, int size) {
        List<UUID> ids = access.myPatientIds();
        Map<UUID, String> names = patients.findAllById(ids).stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getFullName));
        List<AccessLog> logs = accessLogs.findByPatientIdInOrderByCreatedAtDesc(ids, PageRequest.of(page, size)).getContent();
        Map<UUID, User> actors = users.findAllById(logs.stream().map(AccessLog::getActorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return logs.stream().map(l -> {
            User actor = actors.get(l.getActorId());
            String viewer = actor == null ? "Unknown"
                    : doctors.findByUserId(actor.getId()).map(Doctor::getFullName).orElse(
                    actor.getId().equals(access.myPatient().getUserId()) ? "You" : actor.getEmail());
            return new AccessLogDto(l.getCreatedAt(), viewer, actor == null ? null : actor.getRole().name(),
                    l.getResource(), names.get(l.getPatientId()));
        }).toList();
    }

    // ---------- Reception helpers (demographics only) ----------

    @Transactional
    public PatientSummaryDto quickRegister(PatientUpdateRequest req) {
        Patient p = new Patient();
        apply(p, req);
        patients.save(p);
        audit.log("WALKIN_PATIENT_REGISTERED", "Patient", p.getId(), null);
        return PatientSummaryDto.from(p);
    }

    @Transactional(readOnly = true)
    public List<PatientSummaryDto> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String term = q.trim();
        try {
            var dob = java.time.LocalDate.parse(term);
            return patients.findByDob(dob).stream().map(PatientSummaryDto::from).toList();
        } catch (java.time.format.DateTimeParseException ignored) {
            // not a date - search by name or phone
        }
        return patients.searchByNameOrPhone(term, PageRequest.of(0, 25)).stream().map(PatientSummaryDto::from).toList();
    }

    private static void apply(Patient p, PatientUpdateRequest r) {
        p.setFullName(r.fullName());
        p.setDob(r.dob());
        p.setGender(r.gender());
        p.setPhone(r.phone());
        p.setAddress(r.address());
        p.setEmergencyContactName(r.emergencyContactName());
        p.setEmergencyContactPhone(r.emergencyContactPhone());
        p.setBloodGroup(r.bloodGroup());
        p.setInsuranceProvider(r.insuranceProvider());
        p.setInsurancePolicyNo(r.insurancePolicyNo());
        p.setRelationship(r.relationship());
    }
}
