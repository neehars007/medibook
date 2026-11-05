package com.coolapp.medibook.auth;

import com.coolapp.medibook.auth.AuthDtos.*;
import com.coolapp.medibook.common.config.AppProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "mb_refresh";

    private final AuthService auth;
    private final AppProperties props;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> register(@Valid @RequestBody RegisterRequest req) {
        return Map.of("userId", auth.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return withCookie(auth.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        return withCookie(auth.refresh(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest req) {
        auth.verifyEmail(req.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgot(@Valid @RequestBody EmailRequest req) {
        auth.forgotPassword(req.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<AuthResponse> acceptInvite(@Valid @RequestBody AcceptInviteRequest req) {
        return withCookie(auth.acceptInvite(req.token(), req.password()));
    }

    @PostMapping("/2fa/setup")
    public TwoFactorSetupResponse setup2fa() {
        return auth.setupTwoFactor();
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<Void> verify2fa(@Valid @RequestBody TwoFactorVerifyRequest req) {
        auth.verifyTwoFactor(req.code());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> withCookie(IssuedTokens t) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(t.refreshToken(), props.jwt().refreshTtl()).toString())
                .body(t.body());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.security().cookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
