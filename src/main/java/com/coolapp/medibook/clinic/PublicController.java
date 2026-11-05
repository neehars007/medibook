package com.coolapp.medibook.clinic;

import com.coolapp.medibook.appointment.AppointmentType;
import com.coolapp.medibook.appointment.AppointmentTypeRepository;
import com.coolapp.medibook.clinic.ClinicDtos.SpecialtyDto;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.doctor.DoctorDtos.DoctorCard;
import com.coolapp.medibook.doctor.DoctorService;
import com.coolapp.medibook.patient.Gender;
import com.coolapp.medibook.review.ReviewService;
import com.coolapp.medibook.room.RoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Unauthenticated catalog used by the public site and the booking wizard. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicController {

    private final ClinicService clinicService;
    private final SpecialtyRepository specialties;
    private final AppointmentTypeRepository types;
    private final DoctorService doctors;
    private final ReviewService reviews;

    public record PublicClinicDto(String name, String address, String phone, String timezone, String logoUrl,
                                  String about, String emergencyNotice, String paymentPolicy) {}

    public record AppointmentTypeDto(UUID id, UUID specialtyId, String name, int durationMinutes, BigDecimal fee,
                                     RoomType requiresRoomType, boolean followUp, Integer followUpWindowDays,
                                     ConsultationMode mode, boolean intakeRequired, boolean active) {
        public static AppointmentTypeDto from(AppointmentType t) {
            return new AppointmentTypeDto(t.getId(), t.getSpecialtyId(), t.getName(), t.getDurationMinutes(), t.getFee(),
                    t.getRequiresRoomType(), t.isFollowUp(), t.getFollowUpWindowDays(), t.getMode(), t.isIntakeRequired(), t.isActive());
        }
    }

    @GetMapping("/clinic")
    public PublicClinicDto clinic() {
        Clinic c = clinicService.get();
        return new PublicClinicDto(c.getName(), c.getAddress(), c.getPhone(), c.getTimezone(), c.getLogoUrl(), c.getAbout(),
                c.getEmergencyNotice(), c.getPaymentPolicy().name());
    }

    @GetMapping("/specialties")
    public List<SpecialtyDto> specialties() {
        return specialties.findAllByOrderByNameAsc().stream().map(SpecialtyDto::from).toList();
    }

    @GetMapping("/doctors")
    public Page<DoctorCard> doctors(@RequestParam(required = false) UUID specialtyId,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String language,
                                    @RequestParam(required = false) Gender gender,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        return doctors.search(specialtyId, q, language, gender,
                PageRequest.of(page, Math.min(size, 50), Sort.by("fullName")));
    }

    @GetMapping("/doctors/{id}")
    public DoctorCard doctor(@PathVariable UUID id) {
        return doctors.get(id);
    }

    @GetMapping("/doctors/{id}/reviews")
    public Page<ReviewService.PublicReviewDto> doctorReviews(@PathVariable UUID id,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        return reviews.approvedForDoctor(id, PageRequest.of(page, Math.min(size, 50)));
    }

    @GetMapping("/appointment-types")
    public List<AppointmentTypeDto> appointmentTypes(@RequestParam(required = false) UUID specialtyId) {
        List<AppointmentType> list = specialtyId == null ? types.findByActiveTrueOrderByNameAsc()
                : types.findBySpecialtyIdAndActiveTrueOrderByNameAsc(specialtyId);
        return list.stream().map(AppointmentTypeDto::from).toList();
    }
}
