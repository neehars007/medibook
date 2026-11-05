package com.coolapp.medibook.integration;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentSource;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentType;
import com.coolapp.medibook.appointment.AppointmentTypeRepository;
import com.coolapp.medibook.appointment.PaymentMode;
import com.coolapp.medibook.auth.Role;
import com.coolapp.medibook.auth.User;
import com.coolapp.medibook.auth.UserRepository;
import com.coolapp.medibook.clinic.Clinic;
import com.coolapp.medibook.clinic.ClinicRepository;
import com.coolapp.medibook.clinic.Specialty;
import com.coolapp.medibook.clinic.SpecialtyRepository;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.patient.Gender;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.patient.PatientRepository;
import com.coolapp.medibook.room.Room;
import com.coolapp.medibook.room.RoomRepository;
import com.coolapp.medibook.room.RoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Small builders for integration tests. */
@TestComponent
@RequiredArgsConstructor
public class Fixtures {

    private final UserRepository users;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final SpecialtyRepository specialties;
    private final AppointmentTypeRepository types;
    private final RoomRepository rooms;
    private final AppointmentRepository appointments;
    private final ClinicRepository clinics;

    public void clinic() {
        if (clinics.count() == 0) {
            Clinic c = new Clinic();
            c.setName("Test Clinic");
            c.setTimezone("America/New_York");
            clinics.save(c);
        }
    }

    public User user(Role role) {
        User u = new User();
        u.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local");
        u.setRole(role);
        u.setEmailVerified(true);
        return users.save(u);
    }

    public Specialty specialty() {
        Specialty s = new Specialty();
        s.setName("Spec " + UUID.randomUUID());
        return specialties.save(s);
    }

    public AppointmentType type(Specialty s) {
        AppointmentType t = new AppointmentType();
        t.setSpecialtyId(s.getId());
        t.setName("New consultation");
        t.setDurationMinutes(20);
        return types.save(t);
    }

    public Doctor doctor(Specialty s) {
        User u = user(Role.DOCTOR);
        Doctor d = new Doctor();
        d.setUserId(u.getId());
        d.setFullName("Dr. Test " + UUID.randomUUID().toString().substring(0, 6));
        d.setSpecialtyId(s.getId());
        d.setLicenseNo("LIC-" + UUID.randomUUID().toString().substring(0, 6));
        d.setConsultationFee(new BigDecimal("100.00"));
        return doctors.save(d);
    }

    public Room room() {
        Room r = new Room();
        r.setName("Room " + UUID.randomUUID().toString().substring(0, 4));
        r.setType(RoomType.CONSULTATION);
        return rooms.save(r);
    }

    public Patient patient(UUID userId) {
        Patient p = new Patient();
        p.setUserId(userId);
        p.setFullName("Test Patient " + UUID.randomUUID().toString().substring(0, 6));
        p.setDob(LocalDate.of(1990, 1, 1));
        p.setGender(Gender.UNDISCLOSED);
        return patients.save(p);
    }

    public Appointment newAppointment(UUID patientId, UUID doctorId, UUID roomId, UUID typeId, Instant start, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setPatientId(patientId);
        a.setDoctorId(doctorId);
        a.setRoomId(roomId);
        a.setAppointmentTypeId(typeId);
        a.setStartAt(start);
        a.setEndAt(start.plus(Duration.ofMinutes(20)));
        a.setMode(ConsultationMode.IN_PERSON);
        a.setStatus(status);
        a.setSource(AppointmentSource.ONLINE);
        a.setPaymentMode(PaymentMode.PAY_AT_CLINIC);
        return a;
    }

    public Appointment saveAppointment(Appointment a) {
        return appointments.saveAndFlush(a);
    }
}
