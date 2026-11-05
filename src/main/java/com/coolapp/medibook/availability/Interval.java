package com.coolapp.medibook.availability;

import java.time.Duration;
import java.time.Instant;

/** Half-open time interval [start, end), matching PostgreSQL's default tstzrange bounds. */
public record Interval(Instant start, Instant end) {

    public boolean overlaps(Interval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public Interval pad(Duration d) {
        return new Interval(start.minus(d), end.plus(d));
    }
}
