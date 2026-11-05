package com.coolapp.medibook.availability;

import com.coolapp.medibook.doctor.ConsultationMode;
import com.coolapp.medibook.room.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure availability engine (no I/O, fully unit-testable):
 * <ol>
 *   <li>doctor sessions for the weekday intersected with clinic hours</li>
 *   <li>minus leave, existing active appointments (with buffer) and active holds</li>
 *   <li>minus room conflicts when the session/type needs a room</li>
 *   <li>start times by granularity, then minimum-notice and max-advance rules</li>
 * </ol>
 */
public final class SlotCalculator {

    private SlotCalculator() {}

    public record SessionWindow(LocalTime start, LocalTime end, UUID roomId, RoomType roomType, ConsultationMode mode) {}

    public record Request(
            UUID doctorId,
            LocalDate date,
            ZoneId zone,
            List<SessionWindow> sessions,
            LocalTime clinicOpen,
            LocalTime clinicClose,
            int durationMinutes,
            int granularityMinutes,
            int bufferMinutes,
            List<Interval> doctorAppointments,
            List<Interval> doctorBlocked,
            Map<UUID, List<Interval>> roomBusy,
            ConsultationMode requestedMode,
            RoomType requiredRoomType,
            List<UUID> roomsOfRequiredType,
            Instant now,
            Duration minNotice,
            int maxAdvanceDays) {}

    public static List<Slot> compute(Request r) {
        if (r.clinicOpen() == null || r.clinicClose() == null || r.durationMinutes() <= 0) {
            return List.of();
        }
        Instant earliest = r.now().plus(r.minNotice());
        Instant latest = r.now().atZone(r.zone()).toLocalDate().plusDays(r.maxAdvanceDays() + 1L)
                .atStartOfDay(r.zone()).toInstant();
        Duration duration = Duration.ofMinutes(r.durationMinutes());
        Duration buffer = Duration.ofMinutes(r.bufferMinutes());
        int step = Math.max(1, r.granularityMinutes());

        Map<Instant, Slot> result = new LinkedHashMap<>();
        for (SessionWindow s : r.sessions()) {
            if (r.requestedMode() != null && s.mode() != r.requestedMode()) {
                continue;
            }
            LocalTime from = s.start().isAfter(r.clinicOpen()) ? s.start() : r.clinicOpen();
            LocalTime to = s.end().isBefore(r.clinicClose()) ? s.end() : r.clinicClose();
            if (!from.isBefore(to)) {
                continue;
            }
            ZonedDateTime cursor = ZonedDateTime.of(r.date(), from, r.zone());
            ZonedDateTime windowEnd = ZonedDateTime.of(r.date(), to, r.zone());
            while (!cursor.plus(duration).isAfter(windowEnd)) {
                Instant start = cursor.toInstant();
                Interval slot = new Interval(start, start.plus(duration));
                cursor = cursor.plusMinutes(step);

                if (start.isBefore(earliest) || !start.isBefore(latest) || result.containsKey(start)) {
                    continue;
                }
                Interval padded = slot.pad(buffer);
                if (anyOverlap(r.doctorAppointments(), padded) || anyOverlap(r.doctorBlocked(), slot)) {
                    continue;
                }
                UUID room = null;
                if (s.mode() == ConsultationMode.IN_PERSON) {
                    List<UUID> candidates = candidateRooms(s, r);
                    if (candidates == null) {
                        continue; // type needs a room type that doesn't exist
                    }
                    if (!candidates.isEmpty()) {
                        room = candidates.stream()
                                .filter(id -> !anyOverlap(r.roomBusy().getOrDefault(id, List.of()), padded))
                                .findFirst().orElse(null);
                        if (room == null) {
                            continue; // every suitable room is taken
                        }
                    }
                }
                result.put(start, new Slot(r.doctorId(), slot.start(), slot.end(), s.mode(), room));
            }
        }
        List<Slot> slots = new ArrayList<>(result.values());
        slots.sort(Comparator.comparing(Slot::startAt));
        return slots;
    }

    /** null = impossible; empty = no room needed; otherwise rooms to try in order. */
    private static List<UUID> candidateRooms(SessionWindow s, Request r) {
        if (r.requiredRoomType() == null) {
            return s.roomId() == null ? List.of() : List.of(s.roomId());
        }
        List<UUID> list = new ArrayList<>();
        if (s.roomId() != null && s.roomType() == r.requiredRoomType()) {
            list.add(s.roomId());
        }
        for (UUID id : r.roomsOfRequiredType()) {
            if (!list.contains(id)) {
                list.add(id);
            }
        }
        return list.isEmpty() ? null : list;
    }

    private static boolean anyOverlap(List<Interval> busy, Interval candidate) {
        for (Interval b : busy) {
            if (b.overlaps(candidate)) {
                return true;
            }
        }
        return false;
    }
}
