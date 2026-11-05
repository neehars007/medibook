package com.coolapp.medibook.admin;

import com.coolapp.medibook.admin.AdminDtos.AppointmentTypeRequest;
import com.coolapp.medibook.appointment.AppointmentType;
import com.coolapp.medibook.appointment.AppointmentTypeRepository;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.clinic.ClinicDtos.*;
import com.coolapp.medibook.clinic.ClinicHours;
import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.clinic.Holiday;
import com.coolapp.medibook.clinic.PublicController.AppointmentTypeDto;
import com.coolapp.medibook.clinic.Specialty;
import com.coolapp.medibook.clinic.SpecialtyRepository;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.room.Room;
import com.coolapp.medibook.room.RoomDto;
import com.coolapp.medibook.room.RoomMapper;
import com.coolapp.medibook.room.RoomRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Clinic settings, hours, holidays, specialties, rooms and appointment types. ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminClinicController {

    private final ClinicService clinicService;
    private final SpecialtyRepository specialties;
    private final RoomRepository rooms;
    private final RoomMapper roomMapper;
    private final AppointmentTypeRepository types;
    private final AuditService audit;

    // ----- Clinic -----

    @GetMapping("/clinic")
    public ClinicDto clinic() {
        return ClinicDto.from(clinicService.get());
    }

    @PutMapping("/clinic")
    public ClinicDto updateClinic(@Valid @RequestBody ClinicProfileRequest req) {
        return ClinicDto.from(clinicService.updateProfile(req));
    }

    @PutMapping("/clinic/policy")
    public ClinicDto updatePolicy(@Valid @RequestBody ClinicPolicyRequest req) {
        return ClinicDto.from(clinicService.updatePolicy(req));
    }

    @GetMapping("/clinic/hours")
    public List<ClinicHours> hours() {
        return clinicService.allHours();
    }

    @PutMapping("/clinic/hours")
    public List<ClinicHours> updateHours(@Valid @RequestBody List<@Valid HoursRequest> req) {
        return clinicService.updateHours(req);
    }

    @GetMapping("/holidays")
    public List<Holiday> holidays() {
        return clinicService.upcomingHolidays(LocalDate.now().minusDays(1));
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    public Holiday addHoliday(@Valid @RequestBody HolidayRequest req) {
        return clinicService.addHoliday(req.date(), req.name());
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable UUID id) {
        clinicService.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }

    // ----- Specialties -----

    @GetMapping("/specialties")
    public List<SpecialtyDto> specialties() {
        return specialties.findAllByOrderByNameAsc().stream().map(SpecialtyDto::from).toList();
    }

    @PostMapping("/specialties")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SpecialtyDto createSpecialty(@Valid @RequestBody SpecialtyDto req) {
        Specialty s = new Specialty();
        s.setName(req.name());
        s.setDescription(req.description());
        s.setIcon(req.icon());
        specialties.save(s);
        audit.log("SPECIALTY_CREATED", "Specialty", s.getId(), s.getName());
        return SpecialtyDto.from(s);
    }

    @PutMapping("/specialties/{id}")
    @Transactional
    public SpecialtyDto updateSpecialty(@PathVariable UUID id, @Valid @RequestBody SpecialtyDto req) {
        Specialty s = specialties.findById(id).orElseThrow(() -> ApiException.notFound("Specialty"));
        s.setName(req.name());
        s.setDescription(req.description());
        s.setIcon(req.icon());
        audit.log("SPECIALTY_UPDATED", "Specialty", id, null);
        return SpecialtyDto.from(s);
    }

    @DeleteMapping("/specialties/{id}")
    @Transactional
    public ResponseEntity<Void> deleteSpecialty(@PathVariable UUID id) {
        specialties.findById(id).orElseThrow(() -> ApiException.notFound("Specialty")).setDeleted(true);
        audit.log("SPECIALTY_DELETED", "Specialty", id, null);
        return ResponseEntity.noContent().build();
    }

    // ----- Rooms -----

    @GetMapping("/rooms")
    public List<RoomDto> rooms() {
        return rooms.findAllByOrderByNameAsc().stream().map(roomMapper::toDto).toList();
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RoomDto createRoom(@Valid @RequestBody RoomDto req) {
        Room r = new Room();
        roomMapper.update(req, r);
        rooms.save(r);
        audit.log("ROOM_CREATED", "Room", r.getId(), r.getName());
        return roomMapper.toDto(r);
    }

    @PutMapping("/rooms/{id}")
    @Transactional
    public RoomDto updateRoom(@PathVariable UUID id, @Valid @RequestBody RoomDto req) {
        Room r = rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room"));
        roomMapper.update(req, r);
        audit.log("ROOM_UPDATED", "Room", id, null);
        return roomMapper.toDto(r);
    }

    @DeleteMapping("/rooms/{id}")
    @Transactional
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID id) {
        Room r = rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room"));
        r.setActive(false);
        r.setDeleted(true);
        audit.log("ROOM_DELETED", "Room", id, null);
        return ResponseEntity.noContent().build();
    }

    // ----- Appointment types -----

    @GetMapping("/appointment-types")
    public List<AppointmentTypeDto> appointmentTypes() {
        return types.findAll().stream().map(AppointmentTypeDto::from).toList();
    }

    @PostMapping("/appointment-types")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AppointmentTypeDto createType(@Valid @RequestBody AppointmentTypeRequest req) {
        AppointmentType t = new AppointmentType();
        apply(t, req);
        types.save(t);
        audit.log("APPOINTMENT_TYPE_CREATED", "AppointmentType", t.getId(), t.getName());
        return AppointmentTypeDto.from(t);
    }

    @PutMapping("/appointment-types/{id}")
    @Transactional
    public AppointmentTypeDto updateType(@PathVariable UUID id, @Valid @RequestBody AppointmentTypeRequest req) {
        AppointmentType t = types.findById(id).orElseThrow(() -> ApiException.notFound("Appointment type"));
        apply(t, req);
        audit.log("APPOINTMENT_TYPE_UPDATED", "AppointmentType", id, null);
        return AppointmentTypeDto.from(t);
    }

    @DeleteMapping("/appointment-types/{id}")
    @Transactional
    public ResponseEntity<Void> deleteType(@PathVariable UUID id) {
        AppointmentType t = types.findById(id).orElseThrow(() -> ApiException.notFound("Appointment type"));
        t.setActive(false);
        t.setDeleted(true);
        audit.log("APPOINTMENT_TYPE_DELETED", "AppointmentType", id, null);
        return ResponseEntity.noContent().build();
    }

    private void apply(AppointmentType t, AppointmentTypeRequest r) {
        specialties.findById(r.specialtyId()).orElseThrow(() -> ApiException.notFound("Specialty"));
        if (r.followUp() && (r.followUpWindowDays() == null || r.followUpWindowDays() < 1)) {
            throw ApiException.badRequest("Follow-up types need followUpWindowDays");
        }
        t.setSpecialtyId(r.specialtyId());
        t.setName(r.name());
        t.setDurationMinutes(r.durationMinutes());
        t.setFee(r.fee());
        t.setRequiresRoomType(r.requiresRoomType());
        t.setFollowUp(r.followUp());
        t.setFollowUpWindowDays(r.followUpWindowDays());
        t.setMode(r.mode());
        t.setIntakeRequired(r.intakeRequired());
        if (r.active() != null) {
            t.setActive(r.active());
        }
    }
}
