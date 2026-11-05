package com.coolapp.medibook.common.security;

import com.coolapp.medibook.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side sessions in Redis. A session key expires after 15 minutes without activity
 * (clinic security norm), which also invalidates its refresh token.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final AppProperties props;

    public String createSession(UUID userId) {
        String sid = UUID.randomUUID().toString();
        redis.opsForValue().set(sessionKey(sid), userId.toString(), props.security().inactivityTimeout());
        return sid;
    }

    /** Extends the inactivity window. Returns false if the session already timed out or was revoked. */
    public boolean touch(String sid) {
        if (sid == null) {
            return false;
        }
        Boolean ok = redis.expire(sessionKey(sid), props.security().inactivityTimeout());
        return Boolean.TRUE.equals(ok);
    }

    public void invalidate(String sid) {
        if (sid != null) {
            redis.delete(sessionKey(sid));
        }
    }

    public String issueRefreshToken(UUID userId, String sid) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(refreshKey(token), userId + ":" + sid, props.jwt().refreshTtl());
        return token;
    }

    /** One-time use: the token is deleted when consumed (rotation). */
    public Optional<RefreshData> consumeRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redis.opsForValue().getAndDelete(refreshKey(token));
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":", 2);
        return Optional.of(new RefreshData(UUID.fromString(parts[0]), parts[1]));
    }

    public void revokeRefreshToken(String token) {
        if (token != null) {
            redis.delete(refreshKey(token));
        }
    }

    private String sessionKey(String sid) {
        return "session:" + sid;
    }

    private String refreshKey(String token) {
        return "refresh:" + token;
    }

    public record RefreshData(UUID userId, String sessionId) {}
}
