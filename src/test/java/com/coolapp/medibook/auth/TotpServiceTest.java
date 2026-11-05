package com.coolapp.medibook.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238TestVectors() {
        // RFC 6238 Appendix B (SHA-1): T=59 -> 94287082, T=1111111109 -> 07081804 (8 digits)
        assertThat(TotpService.generate(RFC_KEY, 59 / 30, 8)).isEqualTo("94287082");
        assertThat(TotpService.generate(RFC_KEY, 1111111109L / 30, 8)).isEqualTo("07081804");
    }

    @Test
    void base32RoundTrip() {
        byte[] data = "hello medibook".getBytes(StandardCharsets.UTF_8);
        assertThat(TotpService.base32Decode(TotpService.base32Encode(data))).isEqualTo(data);
    }

    @Test
    void verifiesCurrentCodeAndRejectsGarbage() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        TotpService svc = new TotpService(Clock.fixed(now, ZoneOffset.UTC));
        String secret = svc.generateSecret();
        String code = TotpService.generate(TotpService.base32Decode(secret), now.getEpochSecond() / 30, 6);
        assertThat(svc.verify(secret, code)).isTrue();
        assertThat(svc.verify(secret, "abc123")).isFalse();
        assertThat(svc.otpAuthUri(secret, "doc@medibook.test")).startsWith("otpauth://totp/");
    }
}
