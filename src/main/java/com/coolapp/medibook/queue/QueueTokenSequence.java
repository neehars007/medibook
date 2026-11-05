package com.coolapp.medibook.queue;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-doctor, per-day counter. Rows are locked with SELECT ... FOR UPDATE when issuing a token. */
@Entity
@Table(name = "queue_token_sequence")
@Getter
@Setter
@NoArgsConstructor
public class QueueTokenSequence {
    @EmbeddedId
    private QueueTokenKey id;
    @Column(name = "last_value", nullable = false)
    private int lastValue;
}
