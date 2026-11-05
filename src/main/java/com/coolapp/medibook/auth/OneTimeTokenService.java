package com.coolapp.medibook.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Single-use tokens for email verification, password reset and staff invites. */
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    public enum Purpose { VERIFY_EMAIL, RESET_PASSWORD, INVITE }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redis;

    public String issue(Purpose purpose, UUID userId, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(purpose, token), userId.toString(), ttl);
        return token;
    }

    public Optional<UUID> consume(Purpose purpose, String token) {
        String v = redis.opsForValue().getAndDelete(key(purpose, token));
        return Optional.ofNullable(v).map(UUID::fromString);
    }

    private String key(Purpose p, String token) {
        return "ott:" + p.name().toLowerCase() + ":" + token;
    }
}
