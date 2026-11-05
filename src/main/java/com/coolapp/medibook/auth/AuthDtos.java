package com.coolapp.medibook.auth;

import com.coolapp.medibook.patient.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank String phone,
            @NotNull @Past LocalDate dateOfBirth,
            @NotNull Gender gender) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, String totpCode) {}

    public record AuthResponse(String accessToken, long expiresIn, UUID userId, Role role, String email) {}

    public record TokenRequest(@NotBlank String token) {}

    public record EmailRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    public record AcceptInviteRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 100) String password) {}

    public record TwoFactorSetupResponse(String secret, String otpAuthUri) {}

    public record TwoFactorVerifyRequest(@NotBlank String code) {}

    /** Tokens produced by the service; the refresh token goes into an httpOnly cookie, not the body. */
    public record IssuedTokens(AuthResponse body, String refreshToken) {}
}
