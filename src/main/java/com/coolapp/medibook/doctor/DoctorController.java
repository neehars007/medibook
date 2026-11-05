package com.coolapp.medibook.doctor;

import com.coolapp.medibook.appointment.AppointmentDtos.AppointmentDto;
import com.coolapp.medibook.consultation.ConsultationDtos.*;
import com.coolapp.medibook.consultation.ConsultationService;
import com.coolapp.medibook.doctor.DoctorDtos.*;
import com.coolapp.medibook.queue.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything under /doctor requires ROLE_DOCTOR (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;
    private final ConsultationService consultation;
    private final QueueService queue;

    @GetMapping("/me/today")
    public List<DoctorTodayItem> today() {
        return consultation.today();
    }

    @GetMapping("/me/appointments")
    public List<AppointmentDto> appointments(@RequestParam Instant from, @RequestParam Instant to) {
        return consultation.myAppointments(from, to);
    }

    @GetMapping("/me/sessions")
    public List<SessionDto> sessions() {
        return doctorService.mySessions();
    }

    @PutMapping("/me/sessions")
    public List<SessionDto> replaceSessions(@Valid @RequestBody List<@Valid SessionRequest> req) {
        return doctorService.replaceSessions(doctorService.me().getId(), req);
    }

    @GetMapping("/me/leave")
    public List<LeaveDto> leave() {
        return doctorService.myLeave();
    }

    @PostMapping("/me/leave")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveDto addLeave(@Valid @RequestBody LeaveRequest req) {
        return doctorService.addLeave(req);
    }

    @DeleteMapping("/me/leave/{id}")
    public ResponseEntity<Void> deleteLeave(@PathVariable UUID id) {
        doctorService.deleteLeave(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/emergency-block")
    public EmergencyBlockResult emergencyBlock(@Valid @RequestBody EmergencyBlockRequest req) {
        return doctorService.emergencyBlock(doctorService.me().getId(), req);
    }

    /** Only with an active or recent appointment; every view is written to AccessLog. */
    @GetMapping("/patients/{patientId}/chart")
    public ChartDto chart(@PathVariable UUID patientId, @RequestParam(required = false) UUID appointmentId) {
        return consultation.chart(patientId, appointmentId);
    }

    @PostMapping("/queue/next")
    public AppointmentDto callNext() {
        return queue.callNext();
    }

    @PostMapping("/appointments/{id}/start")
    public ResponseEntity<Void> start(@PathVariable UUID id) {
        consultation.start(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/appointments/{id}/visit-note")
    public VisitNoteDto saveDraft(@PathVariable UUID id, @Valid @RequestBody VisitNoteRequest req) {
        return consultation.saveDraft(id, req);
    }

    @PostMapping("/appointments/{id}/visit-note/finalize")
    public VisitNoteDto finalizeNote(@PathVariable UUID id) {
        return consultation.finalizeNote(id);
    }

    @PostMapping("/visit-notes/{id}/addendum")
    @ResponseStatus(HttpStatus.CREATED)
    public AddendumDto addendum(@PathVariable UUID id, @Valid @RequestBody AddendumRequest req) {
        return consultation.addAddendum(id, req.text());
    }

    @PostMapping("/appointments/{id}/no-show")
    public ResponseEntity<Void> noShow(@PathVariable UUID id) {
        consultation.markNoShow(id);
        return ResponseEntity.noContent().build();
    }
}
