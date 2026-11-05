package com.coolapp.medibook.availability;

import com.coolapp.medibook.appointment.AppointmentType;
import com.coolapp.medibook.appointment.AppointmentTypeRepository;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.patient.PatientAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availability;
    private final AppointmentTypeRepository types;
    private final DoctorRepository doctors;
    private final PatientAccessService access;

    public record SlotDto(UUID doctorId, String doctorName, Instant startAt, Instant endAt, ConsultationMode mode) {}

    /** GET /availability?doctorId=|specialtyId=&typeId=&date=&patientId=&mode= */
    @GetMapping
    public List<SlotDto> slots(@RequestParam(required = false) UUID doctorId,
                               @RequestParam(required = false) UUID specialtyId,
                               @RequestParam UUID typeId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) UUID patientId,
                               @RequestParam(required = false) ConsultationMode mode) {
        AppointmentType type = types.findById(typeId).orElseThrow(() -> ApiException.notFound("Appointment type"));
        if (type.isFollowUp() && patientId != null) {
            // Follow-up eligibility reveals visit history: only the patient or front desk may ask.
            if (CurrentUser.optional().isEmpty()) {
                throw ApiException.unauthorized("Log in to see follow-up slots");
            }
            access.requireBookingAccess(patientId);
        }
        var opt = AvailabilityService.Options.of(mode, type.isFollowUp() ? patientId : null);
        List<Slot> slots;
        if (doctorId != null) {
            slots = availability.forDoctor(doctorId, typeId, date, opt);
        } else if (specialtyId != null) {
            slots = availability.forSpecialty(specialtyId, typeId, date, opt);
        } else {
            throw ApiException.badRequest("doctorId or specialtyId is required");
        }
        Map<UUID, String> names = doctors.findAllById(slots.stream().map(Slot::doctorId).distinct().toList())
                .stream().collect(Collectors.toMap(Doctor::getId, Doctor::getFullName));
        return slots.stream()
                .map(s -> new SlotDto(s.doctorId(), names.get(s.doctorId()), s.startAt(), s.endAt(), s.mode()))
                .toList();
    }
}
