package com.coolapp.medibook.integration;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentType;
import com.coolapp.medibook.clinic.Specialty;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.queue.QueueService;
import com.coolapp.medibook.room.Room;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(Fixtures.class)
class DoubleBookingIntegrationTests extends AbstractIntegrationTest {

    private static final int THREADS = 20;

    @Autowired Fixtures fx;
    @Autowired TransactionTemplate tx;
    @Autowired QueueService queue;

    private final Instant slot = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

    @Test
    void twentyConcurrentBookingsOfSameDoctorSlotYieldExactlyOne() throws Exception {
        Specialty s = tx.execute(st -> fx.specialty());
        AppointmentType t = tx.execute(st -> fx.type(s));
        Doctor d = tx.execute(st -> fx.doctor(s));
        List<Patient> pts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            pts.add(tx.execute(st -> fx.patient(null)));
        }
        int ok = race(i -> fx.newAppointment(pts.get(i).getId(), d.getId(), null, t.getId(), slot, AppointmentStatus.CONFIRMED));
        assertThat(ok).isEqualTo(1);
    }

    @Test
    void twentyConcurrentBookingsOfSameRoomYieldExactlyOne() throws Exception {
        Specialty s = tx.execute(st -> fx.specialty());
        AppointmentType t = tx.execute(st -> fx.type(s));
        Room room = tx.execute(st -> fx.room());
        List<Doctor> docs = new ArrayList<>();
        List<Patient> pts = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            docs.add(tx.execute(st -> fx.doctor(s)));
            pts.add(tx.execute(st -> fx.patient(null)));
        }
        int ok = race(i -> fx.newAppointment(pts.get(i).getId(), docs.get(i).getId(), room.getId(), t.getId(), slot, AppointmentStatus.CONFIRMED));
        assertThat(ok).isEqualTo(1);
    }

    @Test
    void patientCannotDoubleBookThemselves() {
        Specialty s = tx.execute(st -> fx.specialty());
        AppointmentType t = tx.execute(st -> fx.type(s));
        Doctor d1 = tx.execute(st -> fx.doctor(s));
        Doctor d2 = tx.execute(st -> fx.doctor(s));
        Patient p = tx.execute(st -> fx.patient(null));
        tx.executeWithoutResult(st -> fx.saveAppointment(fx.newAppointment(p.getId(), d1.getId(), null, t.getId(), slot, AppointmentStatus.CONFIRMED)));
        assertThatThrownBy(() -> tx.executeWithoutResult(st ->
                fx.saveAppointment(fx.newAppointment(p.getId(), d2.getId(), null, t.getId(), slot.plusSeconds(600), AppointmentStatus.CONFIRMED))))
                .hasMessageContaining("no_patient_overlap");
    }

    @Test
    void cancelledAppointmentsDoNotBlockTheSlot() {
        Specialty s = tx.execute(st -> fx.specialty());
        AppointmentType t = tx.execute(st -> fx.type(s));
        Doctor d = tx.execute(st -> fx.doctor(s));
        Patient p1 = tx.execute(st -> fx.patient(null));
        Patient p2 = tx.execute(st -> fx.patient(null));
        tx.executeWithoutResult(st -> fx.saveAppointment(fx.newAppointment(p1.getId(), d.getId(), null, t.getId(), slot, AppointmentStatus.CANCELLED)));
        Appointment second = tx.execute(st -> fx.saveAppointment(fx.newAppointment(p2.getId(), d.getId(), null, t.getId(), slot, AppointmentStatus.CONFIRMED)));
        assertThat(second.getId()).isNotNull();
    }

    @Test
    void concurrentTokenIssuingNeverRepeatsANumber() throws Exception {
        Specialty s = tx.execute(st -> fx.specialty());
        Doctor d = tx.execute(st -> fx.doctor(s));
        LocalDate today = LocalDate.now();
        List<Integer> tokens = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                start.await();
                tokens.add(tx.execute(st -> queue.nextToken(d.getId(), today)));
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(tokens).hasSize(THREADS).doesNotHaveDuplicates();
        assertThat(Collections.max(tokens)).isEqualTo(THREADS);
    }

    private int race(IntFunction<Appointment> build) throws InterruptedException {
        AtomicInteger ok = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < THREADS; i++) {
            int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    tx.executeWithoutResult(st -> fx.saveAppointment(build.apply(n)));
                    ok.incrementAndGet();
                } catch (Exception expected) {
                    // exclusion constraint violation
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        return ok.get();
    }
}
