package com.coolapp.medibook.consultation;

import com.coolapp.medibook.appointment.AppointmentDtos.AppointmentDto;
import com.coolapp.medibook.appointment.AppointmentDtos.IntakeFormDto;
import com.coolapp.medibook.patient.Gender;
import com.coolapp.medibook.patient.PatientDtos.DocumentDto;
import com.coolapp.medibook.patient.PatientDtos.MedicalHistoryDto;
import com.coolapp.medibook.patient.PatientDtos.PatientDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ConsultationDtos {
    private ConsultationDtos() {}

    public record Vitals(@Min(40) @Max(300) Integer systolic, @Min(20) @Max(200) Integer diastolic,
                         @Min(20) @Max(250) Integer pulse, BigDecimal temperatureC, BigDecimal weightKg,
                         @Min(50) @Max(100) Integer spo2) {
        public String display() {
            StringBuilder sb = new StringBuilder();
            if (systolic != null && diastolic != null) sb.append("BP ").append(systolic).append('/').append(diastolic).append(" mmHg; ");
            if (pulse != null) sb.append("Pulse ").append(pulse).append(" bpm; ");
            if (temperatureC != null) sb.append("Temp ").append(temperatureC).append(" C; ");
            if (weightKg != null) sb.append("Weight ").append(weightKg).append(" kg; ");
            if (spo2 != null) sb.append("SpO2 ").append(spo2).append("%; ");
            return sb.length() == 0 ? null : sb.substring(0, sb.length() - 2);
        }
    }

    public record RxItemDto(@NotBlank @Size(max = 200) String medicine, @Size(max = 100) String dosage,
                            @Size(max = 100) String frequency, @Min(1) @Max(365) Integer durationDays,
                            @Size(max = 500) String instructions) {}

    public record VisitNoteRequest(@Size(max = 2000) String chiefComplaint, @Size(max = 8000) String findings,
                                   @Valid Vitals vitals, @Size(max = 2000) String diagnosis, @Size(max = 20) String icdCode,
                                   @Size(max = 4000) String advice, @Size(max = 2000) String recommendedTests,
                                   @Min(1) @Max(365) Integer followUpInDays, @Valid List<RxItemDto> prescription) {}

    public record AddendumRequest(@NotBlank @Size(max = 4000) String text) {}

    public record AddendumDto(UUID id, String text, Instant createdAt, String doctorName) {}

    public record VisitNoteDto(UUID id, UUID appointmentId, String chiefComplaint, String findings, Vitals vitals,
                               String diagnosis, String icdCode, String advice, String recommendedTests,
                               Integer followUpInDays, Instant finalizedAt, List<RxItemDto> prescription,
                               List<AddendumDto> addendums) {}

    public record VisitSummaryDto(UUID appointmentId, Instant visitDate, String doctorName, String typeName,
                                  VisitNoteDto note) {}

    public record DoctorTodayItem(AppointmentDto appointment, int age, Gender gender, IntakeFormDto intake,
                                  List<String> allergies, List<String> conditions) {}

    public record ChartDto(PatientDto patient, int age, List<MedicalHistoryDto> history,
                           List<VisitSummaryDto> previousVisits, List<DocumentDto> documents,
                           IntakeFormDto currentIntake) {}

    public record EmergencyBlockRequest(Instant from, Instant to, @NotBlank @Size(max = 300) String reason) {}

    public record EmergencyBlockResult(int cancelledAppointments) {}
}
