package com.coolapp.medibook.appointment;

import com.coolapp.medibook.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import static com.coolapp.medibook.appointment.AppointmentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentStatusTest {

    @Test
    void happyPathTransitions() {
        assertThat(PENDING_PAYMENT.canTransitionTo(CONFIRMED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(CHECKED_IN)).isTrue();
        assertThat(CHECKED_IN.canTransitionTo(IN_CONSULTATION)).isTrue();
        assertThat(IN_CONSULTATION.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void sideExits() {
        assertThat(PENDING_PAYMENT.canTransitionTo(EXPIRED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(NO_SHOW)).isTrue();
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThat(COMPLETED.canTransitionTo(CANCELLED)).isFalse();
        assertThat(CHECKED_IN.canTransitionTo(CANCELLED)).isFalse();
        assertThat(PENDING_PAYMENT.canTransitionTo(CHECKED_IN)).isFalse();
        assertThat(CANCELLED.canTransitionTo(CONFIRMED)).isFalse();
        assertThat(NO_SHOW.isTerminal()).isTrue();
    }

    @Test
    void activeSetMatchesExclusionConstraints() {
        assertThat(ACTIVE).containsExactlyInAnyOrder(PENDING_PAYMENT, CONFIRMED, CHECKED_IN, IN_CONSULTATION);
    }

    @Test
    void entityTransitionThrowsConflict() {
        Appointment a = new Appointment();
        a.setStatus(COMPLETED);
        assertThatThrownBy(() -> a.transitionTo(CANCELLED)).isInstanceOf(ApiException.class)
                .hasMessageContaining("COMPLETED");
    }
}
