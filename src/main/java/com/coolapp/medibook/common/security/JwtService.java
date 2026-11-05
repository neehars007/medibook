package com.coolapp.medibook.common.security;

import com.coolapp.medibook.auth.Role;
import com.coolapp.medibook.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final AppProperties props;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        String secret = props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("medibook.jwt.secret (JWT_SECRET) is not set");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issueAccessToken(AuthUser user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.id().toString())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .claim("sid", user.sessionId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.jwt().accessTtl())))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(
                    UUID.fromString(c.getSubject()),
                    c.get("email", String.class),
                    Role.valueOf(c.get("role", String.class)),
                    c.get("sid", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return props.jwt().accessTtl().toSeconds();
    }
}
