package com.coolapp.medibook.common.security;

import com.coolapp.medibook.common.config.AppProperties;
import com.coolapp.medibook.common.web.ClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/** Fixed-window rate limits (per IP, per minute) on login, booking and search. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;
    private final AppProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bucket = null;
        int limit = 0;
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && path.startsWith("/api/v1/auth/login")) {
            bucket = "login";
            limit = props.rateLimit().loginPerMinute();
        } else if ("POST".equals(method) && (path.startsWith("/api/v1/holds") || path.equals("/api/v1/appointments"))) {
            bucket = "booking";
            limit = props.rateLimit().bookingPerMinute();
        } else if ("GET".equals(method) && (path.startsWith("/api/v1/doctors") || path.startsWith("/api/v1/availability"))) {
            bucket = "search";
            limit = props.rateLimit().searchPerMinute();
        }

        if (bucket != null && limit > 0) {
            long minute = System.currentTimeMillis() / 60_000;
            String key = "rl:" + bucket + ":" + ClientIp.of(request) + ":" + minute;
            try {
                Long count = redis.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redis.expire(key, Duration.ofSeconds(70));
                }
                if (count != null && count > limit) {
                    response.setStatus(429);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"status\":429,\"detail\":\"Too many requests, slow down\"}");
                    return;
                }
            } catch (RuntimeException e) {
                log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
