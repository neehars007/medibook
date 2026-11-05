package com.coolapp.medibook.availability;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FollowUpPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    @Test
    void eligibleWithinWindow() {
        assertThat(FollowUpPolicy.isEligible(NOW.minus(Duration.ofDays(13)), NOW, 14)).isTrue();
        assertThat(FollowUpPolicy.isEligible(NOW.minus(Duration.ofDays(14)), NOW, 14)).isTrue();
    }

    @Test
    void notEligibleAfterWindowOrWithoutVisit() {
        assertThat(FollowUpPolicy.isEligible(NOW.minus(Duration.ofDays(15)), NOW, 14)).isFalse();
        assertThat(FollowUpPolicy.isEligible(null, NOW, 14)).isFalse();
    }
}
