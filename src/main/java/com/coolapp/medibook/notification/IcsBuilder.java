package com.coolapp.medibook.notification;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Minimal RFC 5545 calendar invite for the confirmation email. */
public final class IcsBuilder {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private IcsBuilder() {}

    public static byte[] build(UUID appointmentId, Instant start, Instant end, String summary, String location,
                               String description, Instant now) {
        String ics = String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//MediBook//Clinic Booking//EN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:" + appointmentId + "@medibook",
                "DTSTAMP:" + UTC.format(now),
                "DTSTART:" + UTC.format(start),
                "DTEND:" + UTC.format(end),
                "SUMMARY:" + escape(summary),
                "LOCATION:" + escape(location == null ? "" : location),
                "DESCRIPTION:" + escape(description == null ? "" : description),
                "BEGIN:VALARM",
                "TRIGGER:-PT2H",
                "ACTION:DISPLAY",
                "DESCRIPTION:Appointment reminder",
                "END:VALARM",
                "END:VEVENT",
                "END:VCALENDAR",
                "");
        return ics.getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }
}
