package com.coolapp.medibook.auth;

import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.auth.AuthDtos.AuthResponse;
import com.coolapp.medibook.auth.AuthDtos.IssuedTokens;
import com.coolapp.medibook.auth.AuthDtos.LoginRequest;
import com.coolapp.medibook.auth.AuthDtos.RegisterRequest;
import com.coolapp.medibook.auth.AuthDtos.TwoFactorSetupResponse;
import com.coolapp.medibook.auth.OneTimeTokenService.Purpose;
import com.coolapp.medibook.common.config.AppProperties;
import com.coolapp.medibook.common.exception.ApiException;
import com.coolapp.medibook.common.security.AuthUser;
import com.coolapp.medibook.common.security.CurrentUser;
import com.coolapp.medibook.common.security.JwtService;
import com.coolapp.medibook.common.security.SessionService;
import com.coolapp.medibook.notification.EmailContent;
import com.coolapp.medibook.notification.EmailSender;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PatientRepository patients;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessions;
    private final TotpService totp;
    private final OneTimeTokenService tokens;
    private final StringRedisTemplate redis;
    private final EmailSender email;
    private final AppProperties props;
    private final AuditService audit;

    @Transactional
    public UUID register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(req.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.PATIENT);
        user.setPhone(req.phone());
        users.save(user);

        Patient p = new Patient();
        p.setUserId(user.getId());
        p.setFullName(req.fullName());
        p.setDob(req.dateOfBirth());
        p.setGender(req.gender());
        p.setPhone(req.phone());
        patients.save(p);

        sendVerification(user);
        audit.log("USER_REGISTERED", "User", user.getId(), null);
        return user.getId();
    }

    @Transactional(readOnly = true)
    public IssuedTokens login(LoginRequest req) {
        String failKey = "login:fail:" + req.email().toLowerCase();
        String failures = redis.opsForValue().get(failKey);
        if (failures != null && Integer.parseInt(failures) >= props.security().maxFailedLogins()) {
            throw new ApiException(HttpStatus.LOCKED, "Account temporarily locked after too many failed logins");
        }
        User user = users.findByEmailIgnoreCase(req.email())
                .filter(u -> u.isActive() && u.getPasswordHash() != null)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            Long n = redis.opsForValue().increment(failKey);
            if (n != null && n == 1L) {
                redis.expire(failKey, props.security().lockoutDuration());
            }
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (user.getRole() == Role.PATIENT && !user.isEmailVerified()) {
            throw ApiException.forbidden("Please verify your email before logging in");
        }
        if (user.isTotpEnabled() && !totp.verify(user.getTotpSecret(), req.totpCode())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Two-factor code required or invalid");
        }
        redis.delete(failKey);
        return issueTokens(user);
    }

    public IssuedTokens refresh(String refreshToken) {
        var data = sessions.consumeRefreshToken(refreshToken)
                .orElseThrow(() -> ApiException.unauthorized("Refresh token invalid or expired"));
        if (!sessions.touch(data.sessionId())) {
            throw ApiException.unauthorized("Session timed out due to inactivity");
        }
        User user = users.findById(data.userId()).filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Account disabled"));
        AuthUser au = new AuthUser(user.getId(), user.getEmail(), user.getRole(), data.sessionId());
        String access = jwtService.issueAccessToken(au);
        String newRefresh = sessions.issueRefreshToken(user.getId(), data.sessionId());
        return new IssuedTokens(new AuthResponse(access, jwtService.accessTtlSeconds(), user.getId(), user.getRole(), user.getEmail()), newRefresh);
    }

    public void logout(String refreshToken) {
        CurrentUser.optional().ifPresent(u -> sessions.invalidate(u.sessionId()));
        sessions.revokeRefreshToken(refreshToken);
    }

    @Transactional
    public void verifyEmail(String token) {
        UUID userId = tokens.consume(Purpose.VERIFY_EMAIL, token)
                .orElseThrow(() -> ApiException.badRequest("Verification link is invalid or expired"));
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.setEmailVerified(true);
        audit.log("EMAIL_VERIFIED", "User", userId, null);
    }

    /** Always succeeds silently so the endpoint can't be used to discover accounts. */
    public void forgotPassword(String emailAddress) {
        users.findByEmailIgnoreCase(emailAddress).filter(User::isActive).ifPresent(user -> {
            String t = tokens.issue(Purpose.RESET_PASSWORD, user.getId(), Duration.ofHours(1));
            email.send(user.getEmail(), "Reset your MediBook password", new EmailContent(
                    "Password reset", "Hello,",
                    List.of("We received a request to reset your password. The link is valid for 1 hour.",
                            "If you didn't ask for this, you can ignore this email."),
                    "Reset password", props.app().frontendUrl() + "/reset-password?token=" + t), null);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        UUID userId = tokens.consume(Purpose.RESET_PASSWORD, token)
                .orElseThrow(() -> ApiException.badRequest("Reset link is invalid or expired"));
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        audit.log("PASSWORD_RESET", "User", userId, null);
    }

    @Transactional
    public IssuedTokens acceptInvite(String token, String password) {
        UUID userId = tokens.consume(Purpose.INVITE, token)
                .orElseThrow(() -> ApiException.badRequest("Invite link is invalid or expired"));
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(true);
        audit.log("INVITE_ACCEPTED", "User", userId, null);
        return issueTokens(user);
    }

    public void sendInvite(User user) {
        String t = tokens.issue(Purpose.INVITE, user.getId(), Duration.ofDays(3));
        email.send(user.getEmail(), "You're invited to MediBook", new EmailContent(
                "Welcome to MediBook", "Hello,",
                List.of("You've been invited to join the clinic team as " + user.getRole().name().toLowerCase() + ".",
                        "Set your password to activate the account. The link is valid for 3 days."),
                "Set password", props.app().frontendUrl() + "/accept-invite?token=" + t), null);
    }

    @Transactional
    public TwoFactorSetupResponse setupTwoFactor() {
        User user = currentUserEntity();
        String secret = totp.generateSecret();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false);
        return new TwoFactorSetupResponse(secret, totp.otpAuthUri(secret, user.getEmail()));
    }

    @Transactional
    public void verifyTwoFactor(String code) {
        User user = currentUserEntity();
        if (user.getTotpSecret() == null || !totp.verify(user.getTotpSecret(), code)) {
            throw ApiException.badRequest("Invalid code");
        }
        user.setTotpEnabled(true);
        audit.log("TOTP_ENABLED", "User", user.getId(), null);
    }

    private void sendVerification(User user) {
        String t = tokens.issue(Purpose.VERIFY_EMAIL, user.getId(), Duration.ofHours(24));
        email.send(user.getEmail(), "Verify your MediBook email", new EmailContent(
                "Confirm your email", "Welcome to MediBook!",
                List.of("Please confirm your email address to start booking appointments."),
                "Verify email", props.app().frontendUrl() + "/verify-email?token=" + t), null);
    }

    private IssuedTokens issueTokens(User user) {
        String sid = sessions.createSession(user.getId());
        AuthUser au = new AuthUser(user.getId(), user.getEmail(), user.getRole(), sid);
        String access = jwtService.issueAccessToken(au);
        String refresh = sessions.issueRefreshToken(user.getId(), sid);
        return new IssuedTokens(new AuthResponse(access, jwtService.accessTtlSeconds(), user.getId(), user.getRole(), user.getEmail()), refresh);
    }

    private User currentUserEntity() {
        return users.findById(CurrentUser.get().id()).orElseThrow(() -> ApiException.notFound("User"));
    }
}
