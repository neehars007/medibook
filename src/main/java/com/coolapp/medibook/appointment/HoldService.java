package com.coolapp.medibook.appointment;

import com.coolapp.medibook.common.config.AppProperties;
import com.coolapp.medibook.doctor.ConsultationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis slot holds. Keys:
 * <ul>
 *   <li>{@code hold:doctor:{doctorId}:{epochSecond}} (SET NX, 10 min TTL) - the lock itself</li>
 *   <li>{@code hold:room:{roomId}:{epochSecond}} - when a room is involved</li>
 *   <li>{@code hold:{holdId}} - hash with the hold details</li>
 *   <li>{@code holds:doctor|room:{id}:{date}} - index sets so availability can subtract holds</li>
 * </ul>
 * The database exclusion constraints remain the final guard.
 */
@Service
@RequiredArgsConstructor
public class HoldService {

    private final StringRedisTemplate redis;
    private final AppProperties props;
    private final Clock clock;

    public Optional<Hold> tryAcquire(UUID userId, UUID doctorId, UUID roomId, UUID typeId, Instant start, Instant end,
                                     ConsultationMode mode, LocalDate day) {
        Duration ttl = props.booking().holdTtl();
        UUID id = UUID.randomUUID();
        String doctorKey = "hold:doctor:" + doctorId + ":" + start.getEpochSecond();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(doctorKey, id.toString(), ttl))) {
            return Optional.empty();
        }
        if (roomId != null) {
            String roomKey = "hold:room:" + roomId + ":" + start.getEpochSecond();
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(roomKey, id.toString(), ttl))) {
                redis.delete(doctorKey);
                return Optional.empty();
            }
        }
        Hold hold = new Hold(id, userId, doctorId, roomId, typeId, start, end, mode, clock.instant().plus(ttl));
        Map<String, String> h = new HashMap<>();
        h.put("userId", userId.toString());
        h.put("doctorId", doctorId.toString());
        h.put("roomId", roomId == null ? "" : roomId.toString());
        h.put("typeId", typeId.toString());
        h.put("startAt", start.toString());
        h.put("endAt", end.toString());
        h.put("mode", mode.name());
        h.put("expiresAt", hold.expiresAt().toString());
        redis.opsForHash().putAll(holdKey(id), h);
        redis.expire(holdKey(id), ttl);
        index("holds:doctor:" + doctorId + ":" + day, id);
        if (roomId != null) {
            index("holds:room:" + roomId + ":" + day, id);
        }
        return Optional.of(hold);
    }

    public Optional<Hold> find(UUID holdId) {
        Map<Object, Object> h = redis.opsForHash().entries(holdKey(holdId));
        if (h.isEmpty()) {
            return Optional.empty();
        }
        String room = (String) h.get("roomId");
        return Optional.of(new Hold(holdId,
                UUID.fromString((String) h.get("userId")),
                UUID.fromString((String) h.get("doctorId")),
                room == null || room.isEmpty() ? null : UUID.fromString(room),
                UUID.fromString((String) h.get("typeId")),
                Instant.parse((String) h.get("startAt")),
                Instant.parse((String) h.get("endAt")),
                ConsultationMode.valueOf((String) h.get("mode")),
                Instant.parse((String) h.get("expiresAt"))));
    }

    public List<Hold> activeForDoctor(UUID doctorId, LocalDate day) {
        return fromIndex("holds:doctor:" + doctorId + ":" + day);
    }

    public List<Hold> activeForRoom(UUID roomId, LocalDate day) {
        return fromIndex("holds:room:" + roomId + ":" + day);
    }

    public void release(Hold hold) {
        redis.delete(List.of(holdKey(hold.id()), "hold:doctor:" + hold.doctorId() + ":" + hold.startAt().getEpochSecond()));
        if (hold.roomId() != null) {
            redis.delete("hold:room:" + hold.roomId() + ":" + hold.startAt().getEpochSecond());
        }
    }

    private List<Hold> fromIndex(String indexKey) {
        Set<String> ids = redis.opsForSet().members(indexKey);
        List<Hold> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (String id : ids) {
            Optional<Hold> h = find(UUID.fromString(id));
            if (h.isPresent()) {
                result.add(h.get());
            } else {
                redis.opsForSet().remove(indexKey, id); // expired, prune
            }
        }
        return result;
    }

    private void index(String key, UUID id) {
        redis.opsForSet().add(key, id.toString());
        redis.expire(key, Duration.ofDays(2));
    }

    private String holdKey(UUID id) {
        return "hold:" + id;
    }
}
