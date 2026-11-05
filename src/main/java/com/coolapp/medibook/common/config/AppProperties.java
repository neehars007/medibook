package com.coolapp.medibook.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Typed view of every {@code medibook.*} property. */
@ConfigurationProperties(prefix = "medibook")
public record AppProperties(
        Jwt jwt,
        Security security,
        Crypto crypto,
        Storage storage,
        Stripe stripe,
        Mail mail,
        App app,
        Booking booking,
        RateLimit rateLimit,
        Video video) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {}

    public record Security(Duration inactivityTimeout, int maxFailedLogins, Duration lockoutDuration, boolean cookieSecure) {}

    public record Crypto(String key) {}

    public record Storage(String endpoint, String accessKey, String secretKey, String bucket,
                          Duration signedUrlTtl, long maxUploadBytes) {}

    public record Stripe(String secretKey, String webhookSecret, String currency) {
        public boolean enabled() {
            return secretKey != null && !secretKey.isBlank();
        }
    }

    public record Mail(String from) {}

    public record App(String frontendUrl, String timezone, boolean seedDemoData) {}

    public record Booking(Duration holdTtl, int chartAccessDays, Duration videoJoinEarly) {}

    public record RateLimit(int loginPerMinute, int bookingPerMinute, int searchPerMinute) {}

    public record Video(String baseUrl) {}
}
