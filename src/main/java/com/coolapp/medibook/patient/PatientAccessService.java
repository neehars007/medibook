package com.coolapp.medibook.patient;

import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.audit.AccessResource;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.auth.Role;
import com.coolapp.medibook.common.config.AppProperties;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.common.security.AuthUser;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Service-layer enforcement of the need-to-know principle. Every read of clinical data goes
 * through {@link #requireClinicalAccess} which also writes the AccessLog row.
 */
@Service
@RequiredArgsConstructor
public class PatientAccessService {

    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    /** The patient record of the logged-in patient user. */
    public Patient myPatient() {
        AuthUser user = CurrentUser.get();
        if (!user.is(Role.PATIENT)) {
            throw ApiException.forbidden("Only patients have a patient profile");
        }
        return patients.findByUserId(user.id()).orElseThrow(() -> ApiException.notFound("Patient profile"));
    }

    /** Self plus dependents. */
    public List<UUID> myPatientIds() {
        Patient me = myPatient();
        List<UUID> ids = new ArrayList<>();
        ids.add(me.getId());
        patients.findByGuardianPatientId(me.getId()).forEach(d -> ids.add(d.getId()));
        return ids;
    }

    public Patient load(UUID patientId) {
        return patients.findById(patientId).orElseThrow(() -> ApiException.notFound("Patient"));
    }

    public boolean isSelfOrGuardian(AuthUser user, Patient patient) {
        if (!user.is(Role.PATIENT)) {
            return false;
        }
        if (Objects.equals(patient.getUserId(), user.id())) {
            return true;
        }
        if (patient.getGuardianPatientId() != null) {
            return patients.findById(patient.getGuardianPatientId())
                    .map(g -> Objects.equals(g.getUserId(), user.id()))
                    .orElse(false);
        }
        return false;
    }

    public boolean isTreatingDoctor(AuthUser user, UUID patientId) {
        if (!user.is(Role.DOCTOR)) {
            return false;
        }
        Doctor doctor = doctors.findByUserId(user.id()).orElse(null);
        if (doctor == null) {
            return false;
        }
        var since = clock.instant().minus(Duration.ofDays(props.booking().chartAccessDays()));
        return appointments.hasTreatingRelationship(doctor.getId(), patientId, AppointmentStatus.ACTIVE, since);
    }

    /** Patient-owned resources (profile, dependents, bookings) - no clinical data implied. */
    public Patient requireOwned(UUID patientId) {
        Patient p = load(patientId);
        if (!isSelfOrGuardian(CurrentUser.get(), p)) {
            throw ApiException.forbidden("Not your patient record");
        }
        return p;
    }

    /** Booking on behalf of a patient: the patient/guardian, or front-desk staff. */
    public Patient requireBookingAccess(UUID patientId) {
        AuthUser user = CurrentUser.get();
        Patient p = load(patientId);
        boolean ok = switch (user.role()) {
            case PATIENT -> isSelfOrGuardian(user, p);
            case RECEPTIONIST, ADMIN, SUPER_ADMIN -> true;
            case DOCTOR -> isTreatingDoctor(user, patientId); // e.g. "book follow-up" button
        };
        if (!ok) {
            throw ApiException.forbidden("You cannot book for this patient");
        }
        return p;
    }

    /** Clinical read: checks need-to-know and records the view in AccessLog. */
    public Patient requireClinicalAccess(UUID patientId, AccessResource resource, Object resourceId) {
        AuthUser user = CurrentUser.get();
        Patient p = load(patientId);
        boolean self = isSelfOrGuardian(user, p);
        boolean treating = !self && isTreatingDoctor(user, patientId);
        if (!ClinicalAccessPolicy.canViewClinical(user.role(), self, treating)) {
            throw ApiException.forbidden("Clinical data is restricted to the patient and treating doctors");
        }
        audit.access(patientId, resource, resourceId);
        return p;
    }

    public Patient requireHistoryEdit(UUID patientId) {
        AuthUser user = CurrentUser.get();
        Patient p = load(patientId);
        boolean self = isSelfOrGuardian(user, p);
        boolean treating = !self && isTreatingDoctor(user, patientId);
        if (!ClinicalAccessPolicy.canEditHistory(user.role(), self, treating)) {
            throw ApiException.forbidden("You cannot edit this patient's history");
        }
        return p;
    }
}
