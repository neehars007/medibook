package com.coolapp.medibook.appointment;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 * PENDING_PAYMENT -> CONFIRMED -> CHECKED_IN -> IN_CONSULTATION -> COMPLETED
 *        |              |    \
 *     EXPIRED       CANCELLED  NO_SHOW
 * </pre>
 * PENDING_PAYMENT may also be CANCELLED. CONFIRMED -> IN_CONSULTATION is allowed only for
 * video visits (no physical check-in); the service enforces that.
 */
public enum AppointmentStatus {
    PENDING_PAYMENT, CONFIRMED, CHECKED_IN, IN_CONSULTATION, COMPLETED, EXPIRED, CANCELLED, NO_SHOW;

    /** Statuses that occupy the doctor/room/patient (mirrors the exclusion constraints). */
    public static final Set<AppointmentStatus> ACTIVE =
            EnumSet.of(PENDING_PAYMENT, CONFIRMED, CHECKED_IN, IN_CONSULTATION);

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSITIONS = new EnumMap<>(AppointmentStatus.class);

    static {
        TRANSITIONS.put(PENDING_PAYMENT, EnumSet.of(CONFIRMED, EXPIRED, CANCELLED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(CHECKED_IN, IN_CONSULTATION, CANCELLED, NO_SHOW));
        TRANSITIONS.put(CHECKED_IN, EnumSet.of(IN_CONSULTATION));
        TRANSITIONS.put(IN_CONSULTATION, EnumSet.of(COMPLETED));
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(AppointmentStatus.class));
        TRANSITIONS.put(EXPIRED, EnumSet.noneOf(AppointmentStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(AppointmentStatus.class));
        TRANSITIONS.put(NO_SHOW, EnumSet.noneOf(AppointmentStatus.class));
    }

    public boolean canTransitionTo(AppointmentStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
