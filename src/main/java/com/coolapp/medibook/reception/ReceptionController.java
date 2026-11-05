package com.coolapp.medibook.reception;

import com.coolapp.medibook.appointment.AppointmentDtos.AppointmentDto;
import com.coolapp.medibook.appointment.AppointmentDtos.BookingResponse;
import com.coolapp.medibook.appointment.AppointmentDtos.ReceptionBookRequest;
import com.coolapp.medibook.appointment.AppointmentDtos.WalkInRequest;
import com.coolapp.medibook.appointment.BookingService;
import com.coolapp.medibook.patient.PatientDtos.PatientSummaryDto;
import com.coolapp.medibook.patient.PatientDtos.PatientUpdateRequest;
import com.coolapp.medibook.patient.PatientService;
import com.coolapp.medibook.payment.PaymentMethod;
import com.coolapp.medibook.queue.QueueService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reception")
@RequiredArgsConstructor
public class ReceptionController {

    private final ReceptionService reception;
    private final PatientService patients;
    private final BookingService booking;
    private final QueueService queue;

    public record CounterPaymentRequest(@NotNull PaymentMethod method, @NotNull @DecimalMin("0.00") BigDecimal amount) {}

    @GetMapping("/today")
    public List<AppointmentDto> today(@RequestParam(required = false) UUID doctorId) {
        return reception.today(doctorId);
    }

    @PostMapping("/patients")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientSummaryDto quickRegister(@Valid @RequestBody PatientUpdateRequest req) {
        return patients.quickRegister(req);
    }

    /** Search by name, phone or DOB (yyyy-MM-dd). */
    @GetMapping("/patients/search")
    public List<PatientSummaryDto> search(@RequestParam String q) {
        return patients.search(q);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@Valid @RequestBody ReceptionBookRequest req) {
        return booking.bookForPatient(req);
    }

    @PostMapping("/appointments/{id}/check-in")
    public AppointmentDto checkIn(@PathVariable UUID id) {
        return reception.checkIn(id);
    }

    @GetMapping("/queue/{doctorId}")
    public QueueService.DoctorQueue queue(@PathVariable UUID doctorId) {
        return queue.forDoctor(doctorId);
    }

    @PostMapping("/queue/{doctorId}/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDto walkIn(@PathVariable UUID doctorId, @Valid @RequestBody WalkInRequest req) {
        return reception.walkIn(doctorId, req);
    }

    @PostMapping("/payments/{appointmentId}")
    public AppointmentDto recordPayment(@PathVariable UUID appointmentId, @Valid @RequestBody CounterPaymentRequest req) {
        return reception.recordPayment(appointmentId, req.method(), req.amount());
    }
}
