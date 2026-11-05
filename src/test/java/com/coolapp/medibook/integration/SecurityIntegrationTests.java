package com.coolapp.medibook.integration;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentType;
import com.coolapp.medibook.auth.Role;
import com.coolapp.medibook.auth.User;
import com.coolapp.medibook.clinic.Specialty;
import com.coolapp.medibook.common.security.AuthUser;
import com.coolapp.medibook.common.security.JwtService;
import com.coolapp.medibook.common.security.SessionService;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.patient.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(Fixtures.class)
class SecurityIntegrationTests extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired Fixtures fx;
    @Autowired TransactionTemplate tx;
    @Autowired JwtService jwt;
    @Autowired SessionService sessions;

    private Patient patient;
    private Appointment appointment;
    private User treatingDoctorUser;
    private User otherDoctorUser;
    private User receptionist;
    private User admin;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(st -> {
            fx.clinic();
            Specialty s = fx.specialty();
            AppointmentType t = fx.type(s);
            Doctor treating = fx.doctor(s);
            Doctor other = fx.doctor(s);
            treatingDoctorUser = new User();
            treatingDoctorUser.setId(treating.getUserId());
            otherDoctorUser = new User();
            otherDoctorUser.setId(other.getUserId());
            patient = fx.patient(null);
            appointment = fx.saveAppointment(fx.newAppointment(patient.getId(), treating.getId(), null, t.getId(),
                    Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS), AppointmentStatus.CONFIRMED));
            receptionist = fx.user(Role.RECEPTIONIST);
            admin = fx.user(Role.ADMIN);
        });
    }

    private String bearer(UUID userId, Role role) {
        String sid = sessions.createSession(userId);
        return "Bearer " + jwt.issueAccessToken(new AuthUser(userId, "x@test.local", role, sid));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/patients/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void receptionistGets403OnVisitNotes() throws Exception {
        mvc.perform(put("/api/v1/doctor/appointments/" + appointment.getId() + "/visit-note")
                        .header("Authorization", bearer(receptionist.getId(), Role.RECEPTIONIST))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"diagnosis\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistAndAdminCannotReadMedicalHistory() throws Exception {
        mvc.perform(get("/api/v1/patients/" + patient.getId() + "/medical-history")
                        .header("Authorization", bearer(receptionist.getId(), Role.RECEPTIONIST)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patients/" + patient.getId() + "/medical-history")
                        .header("Authorization", bearer(admin.getId(), Role.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotReadNonPatientsChart() throws Exception {
        mvc.perform(get("/api/v1/doctor/patients/" + patient.getId() + "/chart")
                        .header("Authorization", bearer(otherDoctorUser.getId(), Role.DOCTOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void treatingDoctorCanReadChart() throws Exception {
        mvc.perform(get("/api/v1/doctor/patients/" + patient.getId() + "/chart")
                        .header("Authorization", bearer(treatingDoctorUser.getId(), Role.DOCTOR)))
                .andExpect(status().isOk());
    }

    @Test
    void expiredSessionIsRejected() throws Exception {
        String token = bearer(receptionist.getId(), Role.RECEPTIONIST);
        var parsed = jwt.parse(token.substring(7)).orElseThrow();
        sessions.invalidate(parsed.sessionId()); // same effect as 15 min of inactivity
        mvc.perform(get("/api/v1/reception/today").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }
}
