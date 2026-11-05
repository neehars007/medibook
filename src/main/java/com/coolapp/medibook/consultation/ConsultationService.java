package com.coolapp.medibook.consultation;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentDtos.IntakeFormDto;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentTypeRepository;
import com.coolapp.medibook.appointment.AppointmentViews;
import com.coolapp.medibook.appointment.IntakeForm;
import com.coolapp.medibook.appointment.IntakeFormRepository;
import com.coolapp.medibook.audit.AccessResource;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.clinic.Clinic;
import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.consultation.ConsultationDtos.*;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.notification.NotificationService;
import com.coolapp.medibook.patient.DocumentService;
import com.coolapp.medibook.patient.MedicalHistory;
import com.coolapp.medibook.patient.MedicalHistoryRepository;
import com.coolapp.medibook.patient.MedicalHistoryType;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.patient.PatientAccessService;
import com.coolapp.medibook.patient.PatientDtos.MedicalHistoryDto;
import com.coolapp.medibook.patient.PatientDtos.PatientDto;
import com.coolapp.medibook.patient.PatientRepository;
import com.coolapp.medibook.prescription.PdfService;
import com.coolapp.medibook.prescription.Prescription;
import com.coolapp.medibook.prescription.PrescriptionItem;
import com.coolapp.medibook.prescription.PrescriptionRepository;
import com.coolapp.medibook.storage.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Doctor's consultation workflow plus the patient's view of finalized visits. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a z");

    private final AppointmentRepository appointments;
    private final AppointmentTypeRepository types;
    private final AppointmentViews views;
    private final IntakeFormRepository intakeForms;
    private final VisitNoteRepository notes;
    private final VisitNoteAddendumRepository addendums;
    private final PrescriptionRepository prescriptions;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final MedicalHistoryRepository history;
    private final PatientAccessService access;
    private final DocumentService documents;
    private final ClinicService clinicService;
    private final NotificationService notifications;
    private final StorageService storage;
    private final PdfService pdf;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    // ---------- Doctor: schedule views ----------

    @Transactional
    public List<DoctorTodayItem> today() {
        Doctor me = currentDoctor();
        ZoneId zone = clinicService.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Appointment> list = appointments.findByDoctorIdAndStartAtBetweenOrderByStartAtAsc(me.getId(),
                today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
                .stream().filter(a -> a.getStatus() != AppointmentStatus.CANCELLED && a.getStatus() != AppointmentStatus.EXPIRED)
                .toList();
        if (list.isEmpty()) {
            return List.of();
        }
        Set<UUID> patientIds = list.stream().map(Appointment::getPatientId).collect(Collectors.toSet());
        Map<UUID, Patient> pts = patients.findAllById(patientIds).stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<UUID, IntakeForm> intake = intakeForms.findByAppointmentIdIn(list.stream().map(Appointment::getId).toList())
                .stream().collect(Collectors.toMap(IntakeForm::getAppointmentId, Function.identity()));
        Map<UUID, List<MedicalHistory>> alerts = history.findByPatientIdInAndActiveTrueAndTypeIn(patientIds,
                        List.of(MedicalHistoryType.ALLERGY, MedicalHistoryType.CONDITION))
                .stream().collect(Collectors.groupingBy(MedicalHistory::getPatientId));
        patientIds.forEach(pid -> audit.access(pid, AccessResource.HISTORY, "today-view"));

        var dtos = views.many(list);
        List<DoctorTodayItem> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Appointment a = list.get(i);
            Patient p = pts.get(a.getPatientId());
            List<MedicalHistory> al = alerts.getOrDefault(a.getPatientId(), List.of());
            out.add(new DoctorTodayItem(dtos.get(i), p.ageOn(today), p.getGender(), IntakeFormDto.from(intake.get(a.getId())),
                    al.stream().filter(h -> h.getType() == MedicalHistoryType.ALLERGY).map(MedicalHistory::getDescription).toList(),
                    al.stream().filter(h -> h.getType() == MedicalHistoryType.CONDITION).map(MedicalHistory::getDescription).toList()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<com.coolapp.medibook.appointment.AppointmentDtos.AppointmentDto> myAppointments(Instant from, Instant to) {
        return views.many(appointments.findByDoctorIdAndStartAtBetweenOrderByStartAtAsc(currentDoctor().getId(), from, to));
    }

    // ---------- Doctor: chart ----------

    @Transactional
    public ChartDto chart(UUID patientId, UUID appointmentId) {
        Patient p = access.requireClinicalAccess(patientId, AccessResource.HISTORY, "chart");
        List<MedicalHistoryDto> hist = history.findByPatientIdOrderByTypeAscCreatedAtDesc(patientId)
                .stream().map(MedicalHistoryDto::from).toList();
        List<VisitSummaryDto> visits = notes.findByPatientIdAndFinalizedAtIsNotNullOrderByFinalizedAtDesc(patientId)
                .stream().map(this::toSummary).toList();
        if (!visits.isEmpty()) {
            audit.access(patientId, AccessResource.NOTE, "chart");
        }
        IntakeFormDto intake = appointmentId == null ? null
                : IntakeFormDto.from(intakeForms.findByAppointmentId(appointmentId).orElse(null));
        return new ChartDto(PatientDto.from(p), p.ageOn(LocalDate.now(clock.withZone(clinicService.zone()))), hist, visits,
                documents.list(patientId), intake);
    }

    // ---------- Doctor: consultation lifecycle ----------

    @Transactional
    public void start(UUID appointmentId) {
        Appointment a = ownAppointment(appointmentId);
        boolean videoReady = a.getMode() == ConsultationMode.VIDEO && a.getStatus() == AppointmentStatus.CONFIRMED;
        if (a.getStatus() != AppointmentStatus.CHECKED_IN && !videoReady) {
            throw ApiException.conflict("Patient must be checked in first");
        }
        a.transitionTo(AppointmentStatus.IN_CONSULTATION);
        a.setConsultStartedAt(clock.instant());
        audit.log("CONSULTATION_STARTED", "Appointment", a.getId(), null);
    }

    @Transactional
    public VisitNoteDto saveDraft(UUID appointmentId, VisitNoteRequest req) {
        Appointment a = ownAppointment(appointmentId);
        if (a.getStatus() != AppointmentStatus.IN_CONSULTATION) {
            throw ApiException.conflict("Start the consultation before writing notes");
        }
        VisitNote n = notes.findByAppointmentId(appointmentId).orElseGet(() -> {
            VisitNote v = new VisitNote();
            v.setAppointmentId(appointmentId);
            v.setDoctorId(a.getDoctorId());
            v.setPatientId(a.getPatientId());
            return v;
        });
        if (n.isFinalized()) {
            throw ApiException.conflict("Note is finalized - add an addendum instead");
        }
        n.setChiefComplaint(req.chiefComplaint());
        n.setFindings(req.findings());
        n.setVitalsJson(writeVitals(req.vitals()));
        n.setDiagnosis(req.diagnosis());
        n.setIcdCode(req.icdCode());
        n.setAdvice(req.advice());
        n.setRecommendedTests(req.recommendedTests());
        n.setFollowUpInDays(req.followUpInDays());
        notes.save(n);

        Prescription rx = prescriptions.findByVisitNoteId(n.getId()).orElse(null);
        List<RxItemDto> items = req.prescription() == null ? List.of() : req.prescription();
        if (!items.isEmpty() || rx != null) {
            if (rx == null) {
                rx = new Prescription();
                rx.setVisitNoteId(n.getId());
            }
            rx.getItems().clear();
            int pos = 0;
            for (RxItemDto i : items) {
                PrescriptionItem item = new PrescriptionItem();
                item.setPosition(pos++);
                item.setMedicine(i.medicine());
                item.setDosage(i.dosage());
                item.setFrequency(i.frequency());
                item.setDurationDays(i.durationDays());
                item.setInstructions(i.instructions());
                rx.getItems().add(item);
            }
            prescriptions.save(rx);
        }
        return toDto(n);
    }

    @Transactional
    public VisitNoteDto finalizeNote(UUID appointmentId) {
        Appointment a = ownAppointment(appointmentId);
        VisitNote n = notes.findByAppointmentId(appointmentId)
                .orElseThrow(() -> ApiException.conflict("Save the note before finalizing"));
        if (n.isFinalized()) {
            throw ApiException.conflict("Already finalized");
        }
        if (n.getDiagnosis() == null || n.getDiagnosis().isBlank()) {
            throw ApiException.badRequest("Diagnosis is required to finalize");
        }
        Instant now = clock.instant();
        n.setFinalizedAt(now);
        a.transitionTo(AppointmentStatus.COMPLETED);
        a.setConsultEndedAt(now);

        try {
            String key = "visits/" + a.getId() + "/summary.pdf";
            storage.put(key, buildSummaryPdf(n, a), "application/pdf");
            n.setSummaryPdfKey(key);
            prescriptions.findByVisitNoteId(n.getId()).filter(rx -> !rx.getItems().isEmpty()).ifPresent(rx -> {
                String rk = "visits/" + a.getId() + "/prescription.pdf";
                storage.put(rk, buildPrescriptionPdf(n, a, rx), "application/pdf");
                rx.setPdfKey(rk);
            });
        } catch (RuntimeException e) {
            log.warn("PDF storage failed for visit {} - will render on demand: {}", a.getId(), e.getMessage());
        }
        notifications.onVisitCompleted(a, n.getFollowUpInDays());
        audit.log("VISIT_FINALIZED", "VisitNote", n.getId(), null);
        return toDto(n);
    }

    @Transactional
    public AddendumDto addAddendum(UUID visitNoteId, String text) {
        Doctor me = currentDoctor();
        VisitNote n = notes.findById(visitNoteId).orElseThrow(() -> ApiException.notFound("Visit note"));
        if (!n.getDoctorId().equals(me.getId())) {
            throw ApiException.forbidden("Only the authoring doctor can add addendums");
        }
        if (!n.isFinalized()) {
            throw ApiException.conflict("Draft notes can be edited directly");
        }
        VisitNoteAddendum ad = new VisitNoteAddendum();
        ad.setVisitNoteId(n.getId());
        ad.setDoctorId(me.getId());
        ad.setText(text);
        addendums.save(ad);
        n.setSummaryPdfKey(null); // regenerate with the addendum on next download
        audit.log("VISIT_ADDENDUM", "VisitNote", n.getId(), null);
        return new AddendumDto(ad.getId(), ad.getText(), ad.getCreatedAt(), me.getFullName());
    }

    @Transactional
    public void markNoShow(UUID appointmentId) {
        Appointment a = ownAppointment(appointmentId);
        if (a.getStartAt().isAfter(clock.instant())) {
            throw ApiException.conflict("Cannot mark a no-show before the start time");
        }
        a.transitionTo(AppointmentStatus.NO_SHOW);
        audit.log("APPOINTMENT_NO_SHOW", "Appointment", a.getId(), null);
    }

    // ---------- Patient-facing ----------

    @Transactional
    public VisitSummaryDto visitSummaryForPatient(UUID appointmentId) {
        VisitNote n = finalizedNote(appointmentId);
        access.requireClinicalAccess(n.getPatientId(), AccessResource.NOTE, n.getId());
        return toSummary(n);
    }

    @Transactional
    public byte[] summaryPdf(UUID appointmentId) {
        VisitNote n = finalizedNote(appointmentId);
        access.requireClinicalAccess(n.getPatientId(), AccessResource.NOTE, n.getId());
        if (n.getSummaryPdfKey() != null) {
            try {
                return storage.get(n.getSummaryPdfKey());
            } catch (RuntimeException e) {
                log.warn("Stored summary missing, regenerating: {}", e.getMessage());
            }
        }
        return buildSummaryPdf(n, appointments.findById(appointmentId).orElseThrow());
    }

    @Transactional
    public byte[] prescriptionPdf(UUID appointmentId) {
        VisitNote n = finalizedNote(appointmentId);
        access.requireClinicalAccess(n.getPatientId(), AccessResource.NOTE, n.getId());
        Prescription rx = prescriptions.findByVisitNoteId(n.getId()).filter(r -> !r.getItems().isEmpty())
                .orElseThrow(() -> ApiException.notFound("Prescription"));
        if (rx.getPdfKey() != null) {
            try {
                return storage.get(rx.getPdfKey());
            } catch (RuntimeException e) {
                log.warn("Stored prescription missing, regenerating: {}", e.getMessage());
            }
        }
        return buildPrescriptionPdf(n, appointments.findById(appointmentId).orElseThrow(), rx);
    }

    // ---------- Helpers ----------

    private VisitNote finalizedNote(UUID appointmentId) {
        return notes.findByAppointmentId(appointmentId).filter(VisitNote::isFinalized)
                .orElseThrow(() -> ApiException.notFound("Visit summary"));
    }

    public Doctor currentDoctor() {
        return doctors.findByUserId(CurrentUser.get().id()).orElseThrow(() -> ApiException.forbidden("Doctor profile required"));
    }

    private Appointment ownAppointment(UUID appointmentId) {
        Doctor me = currentDoctor();
        Appointment a = appointments.findById(appointmentId).orElseThrow(() -> ApiException.notFound("Appointment"));
        if (!a.getDoctorId().equals(me.getId())) {
            throw ApiException.forbidden("Not your appointment");
        }
        return a;
    }

    private VisitSummaryDto toSummary(VisitNote n) {
        Appointment a = appointments.findById(n.getAppointmentId()).orElseThrow();
        String doctor = doctors.findById(n.getDoctorId()).map(Doctor::getFullName).orElse(null);
        String type = types.findById(a.getAppointmentTypeId()).map(t -> t.getName()).orElse(null);
        return new VisitSummaryDto(a.getId(), a.getStartAt(), doctor, type, toDto(n));
    }

    private VisitNoteDto toDto(VisitNote n) {
        List<RxItemDto> rx = prescriptions.findByVisitNoteId(n.getId()).map(p -> p.getItems().stream()
                .map(i -> new RxItemDto(i.getMedicine(), i.getDosage(), i.getFrequency(), i.getDurationDays(), i.getInstructions()))
                .toList()).orElse(List.of());
        List<AddendumDto> ads = n.getId() == null ? List.of() : addendums.findByVisitNoteIdOrderByCreatedAtAsc(n.getId()).stream()
                .map(x -> new AddendumDto(x.getId(), x.getText(), x.getCreatedAt(),
                        doctors.findById(x.getDoctorId()).map(Doctor::getFullName).orElse(null)))
                .toList();
        return new VisitNoteDto(n.getId(), n.getAppointmentId(), n.getChiefComplaint(), n.getFindings(), readVitals(n.getVitalsJson()),
                n.getDiagnosis(), n.getIcdCode(), n.getAdvice(), n.getRecommendedTests(), n.getFollowUpInDays(),
                n.getFinalizedAt(), rx, ads);
    }

    private byte[] buildSummaryPdf(VisitNote n, Appointment a) {
        Clinic c = clinicService.get();
        Patient p = patients.findById(n.getPatientId()).orElseThrow();
        Doctor d = doctors.findById(n.getDoctorId()).orElseThrow();
        VisitNoteDto dto = toDto(n);
        Vitals v = dto.vitals();
        return pdf.visitSummary(new PdfService.VisitSummary(header(c), p.getFullName(), p.getDob().toString(), d.getFullName(),
                d.getLicenseNo(), DATE.format(a.getStartAt().atZone(c.zone())), n.getChiefComplaint(), n.getFindings(),
                v == null ? null : v.display(), n.getDiagnosis(), n.getIcdCode(), n.getAdvice(), n.getRecommendedTests(),
                n.getFollowUpInDays(),
                dto.addendums().stream().map(x -> DATE.format(x.createdAt().atZone(c.zone())) + " - " + x.text()).toList(),
                dto.prescription().stream().map(i -> new PdfService.RxLine(i.medicine(), i.dosage(), i.frequency(),
                        i.durationDays(), i.instructions())).toList()));
    }

    private byte[] buildPrescriptionPdf(VisitNote n, Appointment a, Prescription rx) {
        Clinic c = clinicService.get();
        Patient p = patients.findById(n.getPatientId()).orElseThrow();
        Doctor d = doctors.findById(n.getDoctorId()).orElseThrow();
        return pdf.prescription(new PdfService.PrescriptionDoc(header(c), p.getFullName(), p.getDob().toString(),
                d.getFullName(), d.getLicenseNo(), DATE.format(a.getStartAt().atZone(c.zone())),
                rx.getItems().stream().map(i -> new PdfService.RxLine(i.getMedicine(), i.getDosage(), i.getFrequency(),
                        i.getDurationDays(), i.getInstructions())).toList()));
    }

    private static PdfService.Header header(Clinic c) {
        return new PdfService.Header(c.getName(), c.getAddress(), c.getPhone());
    }

    private String writeVitals(Vitals v) {
        if (v == null) {
            return null;
        }
        try {
            return json.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Invalid vitals");
        }
    }

    private Vitals readVitals(String s) {
        if (s == null) {
            return null;
        }
        try {
            return json.readValue(s, Vitals.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
