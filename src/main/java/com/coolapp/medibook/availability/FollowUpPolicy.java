package com.coolapp.medibook.availability;

import java.time.Duration;
import java.time.Instant;

/** Follow-up types are only offered within N days of a completed visit with the same doctor. */
public final class FollowUpPolicy {

    private FollowUpPolicy() {}

    public static boolean isEligible(Instant lastCompletedVisit, Instant now, int windowDays) {
        if (lastCompletedVisit == null) {
            return false;
        }
        return !lastCompletedVisit.isBefore(now.minus(Duration.ofDays(windowDays)));
    }
}
