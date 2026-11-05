package com.coolapp.medibook.seed;

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
import com.coolapp.medibook.clinic.ClinicHours;
import com.coolapp.medibook.clinic.ClinicHoursRepository;
import com.coolapp.medibook.clinic.ClinicRepository;
import com.coolapp.medibook.clinic.PaymentPolicy;
import com.coolapp.medibook.clinic.Specialty;
import com.coolapp.medibook.clinic.SpecialtyRepository;
import com.coolapp.medibook.common.config.AppProperties;
import com.coolapp.medibook.common.crypto.CryptoKeyHolder;
import com.coolapp.medibook.consultation.VisitNote;
import com.coolapp.medibook.consultation.VisitNoteRepository;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.doctor.DoctorSession;
import com.coolapp.medibook.doctor.DoctorSessionRepository;
import com.coolapp.medibook.patient.Gender;
import com.coolapp.medibook.patient.MedicalHistory;
import com.coolapp.medibook.patient.MedicalHistoryRepository;
import com.coolapp.medibook.patient.MedicalHistoryType;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.patient.PatientRepository;
import com.coolapp.medibook.room.Room;
import com.coolapp.medibook.room.RoomRepository;
import com.coolapp.medibook.room.RoomType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Seeds a demo clinic on first start (dev profile): 4 specialties, 6 doctors, 2 rooms,
 * 20 fake patients and one demo login per role. ALL DATA IS FAKE.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "medibook.app.seed-demo-data", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Demo@12345";

    private final CryptoKeyHolder cryptoKeyHolder; // ensures the encryption key is loaded first
    private final AppProperties props;
    private final ClinicRepository clinics;
    private final ClinicHoursRepository hours;
    private final SpecialtyRepository specialties;
    private final RoomRepository rooms;
    private final AppointmentTypeRepository types;
    private final UserRepository users;
    private final DoctorRepository doctors;
    private final DoctorSessionRepository sessions;
    private final PatientRepository patients;
    private final MedicalHistoryRepository history;
    private final AppointmentRepository appointments;
    private final VisitNoteRepository notes;
    private final PasswordEncoder encoder;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (clinics.count() > 0) {
            return;
        }
        log.info("Seeding demo clinic (fake data)...");
        Faker faker = new Faker(Locale.US, new Random(42));
        String hash = encoder.encode(DEMO_PASSWORD);

        Clinic clinic = new Clinic();
        clinic.setName("Riverside Demo Clinic");
        clinic.setAddress("120 Demo Avenue, Springfield, NJ 07081");
        clinic.setPhone("(555) 010-2000");
        clinic.setTimezone(ZoneId.of(props.app().timezone()).getId());
        clinic.setAbout("A fictional multi-specialty clinic used to demo MediBook.");
        clinic.setEmergencyNotice("This is not for emergencies - call 911.");
        clinic.setPaymentPolicy(PaymentPolicy.EITHER);
        clinics.save(clinic);

        for (DayOfWeek d : DayOfWeek.values()) {
            ClinicHours h = new ClinicHours();
            h.setDayOfWeek(d);
            if (d == DayOfWeek.SUNDAY) {
                h.setClosed(true);
            } else if (d == DayOfWeek.SATURDAY) {
                h.setOpenTime(LocalTime.of(9, 0));
                h.setCloseTime(LocalTime.of(14, 0));
            } else {
                h.setOpenTime(LocalTime.of(8, 0));
                h.setCloseTime(LocalTime.of(20, 0));
            }
            hours.save(h);
        }

        Specialty gm = specialty("General Medicine", "Everyday health concerns, check-ups and chronic care", "stethoscope");
        Specialty peds = specialty("Pediatrics", "Care for babies, children and teens", "baby");
        Specialty derm = specialty("Dermatology", "Skin, hair and nail conditions", "sparkles");
        Specialty dental = specialty("Dental", "Check-ups, cleaning and dental procedures", "smile");

        Room room1 = room("Room 1", RoomType.CONSULTATION);
        Room room2 = room("Room 2", RoomType.PROCEDURE);

        for (Specialty s : List.of(gm, peds, derm, dental)) {
            type(s, "New consultation", 20, null, null, false, null, true);
            type(s, "Follow-up", 10, BigDecimal.ZERO, null, true, 14, false);
            type(s, "Video consultation", 15, null, ConsultationMode.VIDEO, false, null, true);
        }
        AppointmentType procedure = type(dental, "Procedure (cleaning)", 30, new BigDecimal("80.00"), ConsultationMode.IN_PERSON, false, null, false);
        procedure.setRequiresRoomType(RoomType.PROCEDURE);
        AppointmentType dressing = type(gm, "Procedure (dressing)", 30, new BigDecimal("40.00"), ConsultationMode.IN_PERSON, false, null, false);
        dressing.setRequiresRoomType(RoomType.PROCEDURE);

        user("admin@medibook.test", Role.ADMIN, hash);
        user("reception@medibook.test", Role.RECEPTIONIST, hash);

        // Rooms never double-booked at the session level: Room 1 AM/PM, Room 2 AM/PM, two video-first doctors.
        List<Doctor> docs = new ArrayList<>();
        docs.add(doctor("doctor@medibook.test", "Dr. Priya Mehta", Gender.FEMALE, gm, "MBBS, MD (Internal Medicine)", 12, "English,Hindi", "145.00", hash));
        docs.add(doctor("doctor2@medibook.test", "Dr. " + faker.name().fullName(), Gender.MALE, peds, "MD (Pediatrics)", 8, "English,Spanish", "120.00", hash));
        docs.add(doctor("doctor3@medibook.test", "Dr. " + faker.name().fullName(), Gender.FEMALE, dental, "DDS", 10, "English", "100.00", hash));
        docs.add(doctor("doctor4@medibook.test", "Dr. " + faker.name().fullName(), Gender.MALE, dental, "DMD", 6, "English,Portuguese", "100.00", hash));
        docs.add(doctor("doctor5@medibook.test", "Dr. " + faker.name().fullName(), Gender.FEMALE, derm, "MD (Dermatology)", 15, "English,French", "160.00", hash));
        docs.add(doctor("doctor6@medibook.test", "Dr. " + faker.name().fullName(), Gender.MALE, gm, "MBBS", 4, "English,Mandarin", "110.00", hash));

        for (DayOfWeek d : EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) {
            session(docs.get(0), d, 8, 13, room1, ConsultationMode.IN_PERSON);
            session(docs.get(0), d, 14, 17, null, ConsultationMode.VIDEO);
            session(docs.get(1), d, 14, 19, room1, ConsultationMode.IN_PERSON);
            session(docs.get(2), d, 8, 13, room2, ConsultationMode.IN_PERSON);
            session(docs.get(3), d, 14, 19, room2, ConsultationMode.IN_PERSON);
            session(docs.get(4), d, 9, 13, null, ConsultationMode.VIDEO);
            session(docs.get(5), d, 10, 16, null, ConsultationMode.VIDEO);
        }
        session(docs.get(4), DayOfWeek.SATURDAY, 9, 14, room1, ConsultationMode.IN_PERSON);

        // Patients
        User demoUser = user("patient@medibook.test", Role.PATIENT, hash);
        Patient demo = patient(demoUser.getId(), null, "Alex Demo", LocalDate.of(1988, 4, 12), Gender.UNDISCLOSED, faker);
        demo.setBloodGroup("O+");
        demo.setInsuranceProvider("Demo Health Plan");
        demo.setInsurancePolicyNo("DEMO-0000-1234");
        Patient child = patient(null, demo.getId(), "Sam Demo", LocalDate.of(2018, 9, 3), Gender.MALE, faker);
        child.setRelationship("Child");
        historyEntry(demo, MedicalHistoryType.ALLERGY, "Penicillin (rash)");
        historyEntry(demo, MedicalHistoryType.CONDITION, "Mild asthma");
        historyEntry(demo, MedicalHistoryType.MEDICATION, "Salbutamol inhaler as needed");

        for (int i = 0; i < 18; i++) {
            UUID userId = null;
            if (i % 2 == 0) {
                userId = user("patient" + (i + 2) + "@medibook.test", Role.PATIENT, hash).getId();
            }
            Patient p = patient(userId, null, faker.name().fullName(),
                    LocalDate.of(1950 + faker.number().numberBetween(0, 55), faker.number().numberBetween(1, 12), faker.number().numberBetween(1, 28)),
                    i % 2 == 0 ? Gender.FEMALE : Gender.MALE, faker);
            if (i % 3 == 0) {
                historyEntry(p, MedicalHistoryType.ALLERGY, faker.options().option("Peanuts", "Latex", "Sulfa drugs", "Shellfish"));
            }
        }

        // A completed visit 5 days ago, so the demo patient can book a follow-up and the doctor can open the chart.
        AppointmentType gmNew = types.findBySpecialtyIdAndActiveTrueOrderByNameAsc(gm.getId()).stream()
                .filter(t -> t.getName().equals("New consultation")).findFirst().orElseThrow();
        ZoneId zone = clinic.zone();
        var start = LocalDate.now(clock.withZone(zone)).minusDays(5).atTime(9, 0).atZone(zone).toInstant();
        Appointment past = new Appointment();
        past.setPatientId(demo.getId());
        past.setBookedByUserId(demoUser.getId());
        past.setDoctorId(docs.get(0).getId());
        past.setRoomId(room1.getId());
        past.setAppointmentTypeId(gmNew.getId());
        past.setStartAt(start);
        past.setEndAt(start.plus(Duration.ofMinutes(20)));
        past.setMode(ConsultationMode.IN_PERSON);
        past.setStatus(AppointmentStatus.COMPLETED);
        past.setSource(AppointmentSource.ONLINE);
        past.setPaymentMode(PaymentMode.PAY_AT_CLINIC);
        past.setCheckedInAt(start.minus(Duration.ofMinutes(8)));
        past.setConsultStartedAt(start.plus(Duration.ofMinutes(2)));
        past.setConsultEndedAt(start.plus(Duration.ofMinutes(19)));
        past.setTokenNumber(1);
        appointments.save(past);

        VisitNote note = new VisitNote();
        note.setAppointmentId(past.getId());
        note.setDoctorId(docs.get(0).getId());
        note.setPatientId(demo.getId());
        note.setChiefComplaint("Cough for 1 week");
        note.setFindings("Mild wheeze bilaterally, no fever");
        note.setVitalsJson("{\"systolic\":118,\"diastolic\":76,\"pulse\":78,\"temperatureC\":36.8,\"weightKg\":70.5,\"spo2\":98}");
        note.setDiagnosis("Acute bronchitis (demo)");
        note.setAdvice("Rest, fluids, use inhaler as needed");
        note.setFollowUpInDays(7);
        note.setFinalizedAt(past.getConsultEndedAt());
        notes.save(note);

        log.info("Demo data ready. Logins (password {}): admin@, reception@, doctor@, patient@medibook.test", DEMO_PASSWORD);
    }

    private Specialty specialty(String name, String description, String icon) {
        Specialty s = new Specialty();
        s.setName(name);
        s.setDescription(description);
        s.setIcon(icon);
        return specialties.save(s);
    }

    private Room room(String name, RoomType type) {
        Room r = new Room();
        r.setName(name);
        r.setType(type);
        return rooms.save(r);
    }

    private AppointmentType type(Specialty s, String name, int minutes, BigDecimal fee, ConsultationMode mode,
                                 boolean followUp, Integer window, boolean intake) {
        AppointmentType t = new AppointmentType();
        t.setSpecialtyId(s.getId());
        t.setName(name);
        t.setDurationMinutes(minutes);
        t.setFee(fee);
        t.setMode(mode);
        t.setFollowUp(followUp);
        t.setFollowUpWindowDays(window);
        t.setIntakeRequired(intake);
        return types.save(t);
    }

    private User user(String email, Role role, String hash) {
        User u = new User();
        u.setEmail(email);
        u.setRole(role);
        u.setPasswordHash(hash);
        u.setEmailVerified(true);
        u.setPhone("(555) 010-" + String.format("%04d", Math.abs(email.hashCode()) % 10000));
        return users.save(u);
    }

    private Doctor doctor(String email, String name, Gender gender, Specialty s, String quals, int years,
                          String languages, String fee, String hash) {
        User u = user(email, Role.DOCTOR, hash);
        Doctor d = new Doctor();
        d.setUserId(u.getId());
        d.setFullName(name);
        d.setGender(gender);
        d.setSpecialtyId(s.getId());
        d.setQualifications(quals);
        d.setExperienceYears(years);
        d.setLanguages(languages);
        d.setBio("Fictional doctor profile for the MediBook demo.");
        d.setLicenseNo("DEMO-" + Math.abs(email.hashCode() % 100000));
        d.setConsultationFee(new BigDecimal(fee));
        return doctors.save(d);
    }

    private void session(Doctor d, DayOfWeek day, int fromHour, int toHour, Room room, ConsultationMode mode) {
        DoctorSession s = new DoctorSession();
        s.setDoctorId(d.getId());
        s.setDayOfWeek(day);
        s.setStartTime(LocalTime.of(fromHour, 0));
        s.setEndTime(LocalTime.of(toHour, 0));
        s.setRoomId(room == null ? null : room.getId());
        s.setMode(mode);
        sessions.save(s);
    }

    private Patient patient(UUID userId, UUID guardianId, String name, LocalDate dob, Gender gender, Faker faker) {
        Patient p = new Patient();
        p.setUserId(userId);
        p.setGuardianPatientId(guardianId);
        p.setFullName(name);
        p.setDob(dob);
        p.setGender(gender);
        p.setPhone("(555) 01" + faker.number().digits(1) + "-" + faker.number().digits(4));
        p.setAddress(faker.address().streetAddress() + ", Springfield");
        p.setEmergencyContactName(faker.name().fullName());
        p.setEmergencyContactPhone("(555) 019-" + faker.number().digits(4));
        return patients.save(p);
    }

    private void historyEntry(Patient p, MedicalHistoryType type, String description) {
        MedicalHistory h = new MedicalHistory();
        h.setPatientId(p.getId());
        h.setType(type);
        h.setDescription(description);
        history.save(h);
    }
}
