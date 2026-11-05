package com.coolapp.medibook.queue;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface QueueTokenSequenceRepository extends JpaRepository<QueueTokenSequence, QueueTokenKey> {

    /** Creates today's row if missing; a no-op (not an error) when another transaction got there first. */
    @Modifying
    @Query(value = "insert into queue_token_sequence (doctor_id, day, last_value) values (:doctorId, :day, 0) on conflict do nothing",
            nativeQuery = true)
    void ensureRow(@Param("doctorId") UUID doctorId, @Param("day") LocalDate day);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QueueTokenSequence s where s.id = :id")
    Optional<QueueTokenSequence> lockById(@Param("id") QueueTokenKey id);
}
