package com.coolapp.medibook.patient;

import com.coolapp.medibook.audit.AccessResource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PatientDtos {
    private PatientDtos() {}

    public record PatientDto(UUID id, UUID guardianPatientId, String relationship, String fullName, LocalDate dob,
                             Gender gender, String phone, String address, String emergencyContactName,
                             String emergencyContactPhone, String bloodGroup, String insuranceProvider,
                             String insurancePolicyNo) {
        public static PatientDto from(Patient p) {
            return new PatientDto(p.getId(), p.getGuardianPatientId(), p.getRelationship(), p.getFullName(), p.getDob(),
                    p.getGender(), p.getPhone(), p.getAddress(), p.getEmergencyContactName(),
                    p.getEmergencyContactPhone(), p.getBloodGroup(), p.getInsuranceProvider(), p.getInsurancePolicyNo());
        }
    }

    /** Demographics only - what the front desk may see. */
    public record PatientSummaryDto(UUID id, String fullName, LocalDate dob, Gender gender, String phone, boolean hasAccount) {
        public static PatientSummaryDto from(Patient p) {
            return new PatientSummaryDto(p.getId(), p.getFullName(), p.getDob(), p.getGender(), p.getPhone(), p.getUserId() != null);
        }
    }

    public record PatientUpdateRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotNull @Past LocalDate dob,
            @NotNull Gender gender,
            String phone, String address, String emergencyContactName, String emergencyContactPhone,
            @Size(max = 10) String bloodGroup, String insuranceProvider, String insurancePolicyNo,
            String relationship) {}

    public record MedicalHistoryDto(UUID id, MedicalHistoryType type, String description, LocalDate since, boolean active) {
        public static MedicalHistoryDto from(MedicalHistory h) {
            return new MedicalHistoryDto(h.getId(), h.getType(), h.getDescription(), h.getSince(), h.isActive());
        }
    }

    public record MedicalHistoryRequest(@NotNull MedicalHistoryType type, @NotBlank @Size(max = 2000) String description,
                                        LocalDate since, Boolean active) {}

    public record DocumentDto(UUID id, String fileName, String mimeType, long sizeBytes, DocumentType docType, Instant uploadedAt) {
        public static DocumentDto from(PatientDocument d) {
            return new DocumentDto(d.getId(), d.getFileName(), d.getMimeType(), d.getSizeBytes(), d.getDocType(), d.getUploadedAt());
        }
    }

    public record SignedUrlDto(String url, Instant expiresAt) {}

    public record AccessLogDto(Instant viewedAt, String viewer, String viewerRole, AccessResource resource, String patientName) {}
}
