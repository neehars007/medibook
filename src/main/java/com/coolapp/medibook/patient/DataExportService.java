package com.coolapp.medibook.patient;

import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentViews;
import com.coolapp.medibook.audit.AccessResource;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.consultation.ConsultationService;
import com.coolapp.medibook.consultation.VisitNote;
import com.coolapp.medibook.consultation.VisitNoteRepository;
import com.coolapp.medibook.patient.PatientDtos.DocumentDto;
import com.coolapp.medibook.patient.PatientDtos.MedicalHistoryDto;
import com.coolapp.medibook.patient.PatientDtos.PatientDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** "Download all my data" (privacy rights): profile, dependents, history, visits, documents. */
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final PatientAccessService access;
    private final PatientRepository patients;
    private final MedicalHistoryRepository history;
    private final PatientDocumentRepository documents;
    private final AppointmentRepository appointments;
    private final AppointmentViews views;
    private final VisitNoteRepository notes;
    private final ConsultationService consultation;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public Map<String, Object> exportMine() {
        List<UUID> ids = access.myPatientIds();
        List<Map<String, Object>> people = new ArrayList<>();
        for (UUID id : ids) {
            Patient p = access.requireClinicalAccess(id, AccessResource.HISTORY, "export");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("profile", PatientDto.from(p));
            m.put("medicalHistory", history.findByPatientIdOrderByTypeAscCreatedAtDesc(id).stream().map(MedicalHistoryDto::from).toList());
            m.put("appointments", views.many(appointments.findByPatientIdOrderByStartAtDesc(id)));
            m.put("visits", notes.findByPatientIdAndFinalizedAtIsNotNullOrderByFinalizedAtDesc(id).stream()
                    .map(VisitNote::getAppointmentId).map(consultation::visitSummaryForPatient).toList());
            m.put("documents", documents.findByPatientIdOrderByUploadedAtDesc(id).stream().map(DocumentDto::from).toList());
            people.add(m);
        }
        audit.log("DATA_EXPORTED", "Patient", ids.get(0), ids.size() + " record(s)");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", clock.instant());
        out.put("patients", people);
        return out;
    }
}
