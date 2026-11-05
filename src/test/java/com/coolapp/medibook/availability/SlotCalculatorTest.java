package com.coolapp.medibook.availability;

import com.coolapp.medibook.availability.SlotCalculator.Request;
import com.coolapp.medibook.availability.SlotCalculator.SessionWindow;
import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.room.RoomType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10); // Tuesday
    private static final UUID DOCTOR = UUID.randomUUID();
    private static final UUID ROOM_A = UUID.randomUUID();
    private static final UUID PROC_ROOM = UUID.randomUUID();
    private static final Instant NOW = at(7, 0).minus(Duration.ofDays(1));

    private static Instant at(int h, int m) {
        return ZonedDateTime.of(DAY, LocalTime.of(h, m), ZONE).toInstant();
    }

    private static Request req(List<SessionWindow> sessions, LocalTime open, LocalTime close, int duration,
                               List<Interval> busy, List<Interval> blocked, Map<UUID, List<Interval>> rooms,
                               ConsultationMode mode, RoomType required, List<UUID> requiredRooms, Instant now) {
        return new Request(DOCTOR, DAY, ZONE, sessions, open, close, duration, 10, 5, busy, blocked, rooms,
                mode, required, requiredRooms, now, Duration.ofMinutes(60), 30);
    }

    private static SessionWindow morning() {
        return new SessionWindow(LocalTime.of(9, 0), LocalTime.of(10, 0), ROOM_A, RoomType.CONSULTATION, ConsultationMode.IN_PERSON);
    }

    @Test
    void generatesSlotsByGranularityWithinSession() {
        var slots = SlotCalculator.compute(req(List.of(morning()), LocalTime.of(8, 0), LocalTime.of(20, 0), 20,
                List.of(), List.of(), Map.of(), null, null, List.of(), NOW));
        // 9:00 .. 9:40 every 10 min (9:40 + 20 = 10:00)
        assertThat(slots).extracting(Slot::startAt)
                .containsExactly(at(9, 0), at(9, 10), at(9, 20), at(9, 30), at(9, 40));
        assertThat(slots).allMatch(s -> ROOM_A.equals(s.roomId()));
    }

    @Test
    void intersectsWithClinicHours() {
        var slots = SlotCalculator.compute(req(List.of(morning()), LocalTime.of(9, 30), LocalTime.of(20, 0), 20,
                List.of(), List.of(), Map.of(), null, null, List.of(), NOW));
        assertThat(slots).extracting(Slot::startAt).containsExactly(at(9, 30), at(9, 40));
    }

    @Test
    void subtractsAppointmentsWithBuffer() {
        // existing 9:20-9:40; with a 5 min buffer, anything overlapping 9:15-9:45 is gone
        var busy = List.of(new Interval(at(9, 20), at(9, 40)));
        var slots = SlotCalculator.compute(req(List.of(morning()), LocalTime.of(8, 0), LocalTime.of(20, 0), 10,
                busy, List.of(), Map.of(), null, null, List.of(), NOW));
        assertThat(slots).extracting(Slot::startAt)
                .containsExactly(at(9, 0), at(9, 50));
    }

    @Test
    void subtractsLeave() {
        var leave = List.of(new Interval(at(9, 0), at(9, 30)));
        var slots = SlotCalculator.compute(req(List.of(morning()), LocalTime.of(8, 0), LocalTime.of(20, 0), 20,
                List.of(), leave, Map.of(), null, null, List.of(), NOW));
        assertThat(slots).extracting(Slot::startAt).containsExactly(at(9, 30), at(9, 40));
    }

    @Test
    void roomConflictRemovesSlot() {
        var rooms = Map.of(ROOM_A, List.of(new Interval(at(9, 0), at(9, 30))));
        var slots = SlotCalculator.compute(req(List.of(morning()), LocalTime.of(8, 0), LocalTime.of(20, 0), 20,
                List.of(), List.of(), rooms, null, null, List.of(), NOW));
        assertThat(slots).extracting(Slot::startAt).containsExactly(at(9, 40));
    }

    @Test
    void requiredRoomTypeUsesAnyFreeRoomOfThatType() {
        var slots = SlotCalculator.compute(req(List.of(morning()), LocalTime.of(8, 0), LocalTime.of(20, 0), 30,
                List.of(), List.of(), Map.of(PROC_ROOM, List.of(new Interval(at(9, 0), at(9, 25)))),
                null, RoomType.PROCEDURE, List.of(PROC_ROOM), NOW));
        assertThat(slots).extracting(Slot::startAt).containsExactly(at(9, 30));
        assertThat(slots.get(0).roomId()).isEqualTo(PROC_ROOM);
    }

    @Test
    void requiredRoomTypeMissingMeansNoSlots() {
        var slots = SlotCalculator.compute(req(List.of(morning()), LocalTime.of(8, 0), LocalTime.of(20, 0), 30,
                List.of(), List.of(), Map.of(), null, RoomType.XRAY, List.of(), NOW));
        assertThat(slots).isEmpty();
    }

    @Test
    void minimumNoticeApplies() {
        Instant now = at(8, 30); // + 60 min notice = 9:30
        var slots = SlotCalculator.compute(req(List.of(morning()), LocalTime.of(8, 0), LocalTime.of(20, 0), 20,
                List.of(), List.of(), Map.of(), null, null, List.of(), now));
        assertThat(slots).extracting(Slot::startAt).containsExactly(at(9, 30), at(9, 40));
    }

    @Test
    void maxAdvanceWindowApplies() {
        Instant now = at(9, 0).minus(Duration.ofDays(40));
        var slots = SlotCalculator.compute(req(List.of(morning()), LocalTime.of(8, 0), LocalTime.of(20, 0), 20,
                List.of(), List.of(), Map.of(), null, null, List.of(), now));
        assertThat(slots).isEmpty();
    }

    @Test
    void filtersByMode() {
        var video = new SessionWindow(LocalTime.of(14, 0), LocalTime.of(14, 30), null, null, ConsultationMode.VIDEO);
        var slots = SlotCalculator.compute(req(List.of(morning(), video), LocalTime.of(8, 0), LocalTime.of(20, 0), 15,
                List.of(), List.of(), Map.of(), ConsultationMode.VIDEO, null, List.of(), NOW));
        assertThat(slots).extracting(Slot::startAt).containsExactly(at(14, 0), at(14, 10));
        assertThat(slots).allMatch(s -> s.roomId() == null && s.mode() == ConsultationMode.VIDEO);
    }
}
