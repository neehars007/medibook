package com.coolapp.medibook.reception;

import com.coolapp.medibook.appointment.Appointment;
import com.coolapp.medibook.appointment.AppointmentDtos.AppointmentDto;
import com.coolapp.medibook.appointment.AppointmentDtos.WalkInRequest;
import com.coolapp.medibook.appointment.AppointmentRepository;
import com.coolapp.medibook.appointment.AppointmentStatus;
import com.coolapp.medibook.appointment.AppointmentViews;
import com.coolapp.medibook.appointment.BookingService;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.payment.Payment;
import com.coolapp.medibook.payment.PaymentMethod;
import com.coolapp.medibook.payment.PaymentService;
import com.coolapp.medibook.queue.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Front desk. Works only with demographics and logistics - never clinical notes. */
@Service
@RequiredArgsConstructor
public class ReceptionService {

    private final AppointmentRepository appointments;
    private final AppointmentViews views;
    private final BookingService booking;
    private final QueueService queue;
    private final PaymentService payments;
    private final ClinicService clinicService;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AppointmentDto> today(UUID doctorId) {
        ZoneId zone = clinicService.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        var from = today.atStartOfDay(zone).toInstant();
        var to = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> list = doctorId == null
                ? appointments.findByStartAtBetweenOrderByDoctorIdAscStartAtAsc(from, to)
                : appointments.findByDoctorIdAndStartAtBetweenOrderByStartAtAsc(doctorId, from, to);
        return views.many(list.stream().filter(a -> a.getStatus() != AppointmentStatus.EXPIRED).toList());
    }

    @Transactional
    public AppointmentDto checkIn(UUID appointmentId) {
        Appointment a = booking.load(appointmentId);
        LocalDate today = LocalDate.now(clock.withZone(clinicService.zone()));
        if (!a.getStartAt().atZone(clinicService.zone()).toLocalDate().equals(today)) {
            throw ApiException.conflict("Check-in is only possible on the day of the appointment");
        }
        if (a.getMode() == ConsultationMode.VIDEO) {
            throw ApiException.conflict("Video consultations don't need check-in");
        }
        a.transitionTo(AppointmentStatus.CHECKED_IN);
        a.setCheckedInAt(clock.instant());
        a.setTokenNumber(queue.nextToken(a.getDoctorId(), today));
        audit.log("PATIENT_CHECKED_IN", "Appointment", a.getId(), "token " + a.getTokenNumber());
        return views.one(a);
    }

    @Transactional
    public AppointmentDto walkIn(UUID doctorId, WalkInRequest req) {
        Appointment a = booking.bookWalkIn(doctorId, req);
        return checkIn(a.getId());
    }

    @Transactional
    public AppointmentDto recordPayment(UUID appointmentId, PaymentMethod method, BigDecimal amount) {
        Appointment a = booking.load(appointmentId);
        Payment p = payments.recordCounterPayment(a, method, amount);
        audit.log("COUNTER_PAYMENT", "Payment", p.getId(), method + " " + amount);
        return views.one(a);
    }
}
