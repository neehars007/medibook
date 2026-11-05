package com.coolapp.medibook.admin;

import com.coolapp.medibook.admin.AdminDtos.StaffDto;
import com.coolapp.medibook.admin.AdminDtos.StaffInviteRequest;
import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.auth.AuthService;
import com.coolapp.medibook.auth.Role;
import com.coolapp.medibook.auth.User;
import com.coolapp.medibook.auth.UserRepository;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.doctor.DoctorDtos.*;
import com.coolapp.medibook.doctor.DoctorService;
import com.coolapp.medibook.doctor.DoctorSessionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminStaffController {

    private final DoctorService doctors;
    private final DoctorSessionRepository sessions;
    private final UserRepository users;
    private final AuthService auth;
    private final AuditService audit;

    @GetMapping("/doctors")
    public List<DoctorCard> doctors() {
        return doctors.all();
    }

    @PostMapping("/doctors/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorCard invite(@Valid @RequestBody DoctorInviteRequest req) {
        return doctors.invite(req);
    }

    @PutMapping("/doctors/{id}")
    public DoctorCard update(@PathVariable UUID id, @Valid @RequestBody DoctorProfileRequest req) {
        return doctors.update(id, req);
    }

    @GetMapping("/doctors/{id}/sessions")
    public List<SessionDto> sessions(@PathVariable UUID id) {
        return sessions.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(id).stream().map(SessionDto::from).toList();
    }

    @PutMapping("/doctors/{id}/sessions")
    public List<SessionDto> replaceSessions(@PathVariable UUID id, @Valid @RequestBody List<@Valid SessionRequest> req) {
        return doctors.replaceSessions(id, req);
    }

    @GetMapping("/staff")
    public List<StaffDto> staff() {
        return users.findByRoleIn(List.of(Role.RECEPTIONIST, Role.ADMIN)).stream().map(AdminStaffController::dto).toList();
    }

    @PostMapping("/staff/invite")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public StaffDto inviteStaff(@Valid @RequestBody StaffInviteRequest req) {
        if (req.role() != Role.RECEPTIONIST && req.role() != Role.ADMIN) {
            throw ApiException.badRequest("Use /admin/doctors/invite for doctors");
        }
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Email already in use");
        }
        User u = new User();
        u.setEmail(req.email().trim().toLowerCase());
        u.setPhone(req.phone());
        u.setRole(req.role());
        users.save(u);
        auth.sendInvite(u);
        audit.log("STAFF_INVITED", "User", u.getId(), req.role().name());
        return dto(u);
    }

    @PatchMapping("/staff/{id}/active")
    @Transactional
    public StaffDto setActive(@PathVariable UUID id, @RequestParam boolean active) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getRole() == Role.PATIENT) {
            throw ApiException.badRequest("Not a staff account");
        }
        u.setActive(active);
        audit.log(active ? "STAFF_ENABLED" : "STAFF_DISABLED", "User", id, null);
        return dto(u);
    }

    private static StaffDto dto(User u) {
        return new StaffDto(u.getId(), u.getEmail(), u.getPhone(), u.getRole(), u.isActive(), u.isTotpEnabled(), u.getCreatedAt());
    }
}
