package com.coolapp.medibook.report;

import com.coolapp.medibook.clinic.ClinicService;
import com.coolapp.medibook.doctor.DoctorSession;
import com.coolapp.medibook.doctor.DoctorSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Operational reports. Queries touch scheduling and payment columns only - never clinical data. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicService clinicService;
    private final DoctorSessionRepository sessions;
    private final Clock clock;

    public record TodaySummary(LocalDate date, long total, long checkedIn, long waiting, long inConsultation,
                               long completed, long noShows, long cancelled) {}

    public record DoctorStats(UUID doctorId, String doctorName, String specialty, long booked, long completed,
                              long noShows, double bookedMinutes, double sessionMinutes, double utilizationPct,
                              Double avgConsultMinutes, Double avgWaitMinutes) {}

    public record Revenue(BigDecimal total, Map<String, BigDecimal> byDoctor, Map<String, BigDecimal> bySpecialty,
                          Map<String, BigDecimal> byPaymentMode) {}

    public record PatientStats(long newPatients, long returningPatients, long appointments, long cancelled,
                               long noShows, double cancellationRatePct, double noShowRatePct) {}

    public record HeatCell(int isoDayOfWeek, int hour, long count) {}

    public record Range(LocalDate from, LocalDate to) {}

    @Transactional(readOnly = true)
    public TodaySummary today() {
        ZoneId zone = clinicService.zone();
        LocalDate d = LocalDate.now(clock.withZone(zone));
        Map<String, Long> by = new TreeMap<>();
        jdbc.query("select status, count(*) c from appointment where deleted = false and start_at >= :from and start_at < :to group by status",
                range(d, d, zone), rs -> {
                    by.put(rs.getString("status"), rs.getLong("c"));
                });
        long checkedIn = by.getOrDefault("CHECKED_IN", 0L) + by.getOrDefault("IN_CONSULTATION", 0L) + by.getOrDefault("COMPLETED", 0L);
        long total = by.entrySet().stream().filter(e -> !e.getKey().equals("EXPIRED")).mapToLong(Map.Entry::getValue).sum();
        return new TodaySummary(d, total, checkedIn, by.getOrDefault("CHECKED_IN", 0L), by.getOrDefault("IN_CONSULTATION", 0L),
                by.getOrDefault("COMPLETED", 0L), by.getOrDefault("NO_SHOW", 0L), by.getOrDefault("CANCELLED", 0L));
    }

    @Transactional(readOnly = true)
    public List<DoctorStats> doctors(LocalDate from, LocalDate to) {
        ZoneId zone = clinicService.zone();
        String sql = """
                select d.id, d.full_name, s.name as specialty,
                  count(a.id) filter (where a.status not in ('CANCELLED','EXPIRED')) as booked,
                  count(a.id) filter (where a.status = 'COMPLETED') as completed,
                  count(a.id) filter (where a.status = 'NO_SHOW') as no_shows,
                  coalesce(sum(extract(epoch from (a.end_at - a.start_at)) / 60.0) filter (where a.status not in ('CANCELLED','EXPIRED')), 0) as booked_minutes,
                  avg(extract(epoch from (a.consult_ended_at - a.consult_started_at)) / 60.0) filter (where a.consult_ended_at is not null and a.consult_started_at is not null) as avg_consult,
                  avg(extract(epoch from (a.consult_started_at - a.checked_in_at)) / 60.0) filter (where a.consult_started_at is not null and a.checked_in_at is not null) as avg_wait
                from doctor d
                join specialty s on s.id = d.specialty_id
                left join appointment a on a.doctor_id = d.id and a.deleted = false and a.start_at >= :from and a.start_at < :to
                where d.deleted = false
                group by d.id, d.full_name, s.name
                order by d.full_name""";
        return jdbc.query(sql, range(from, to, zone), (rs, i) -> {
            UUID id = rs.getObject("id", UUID.class);
            double booked = rs.getDouble("booked_minutes");
            double available = sessionMinutes(id, from, to);
            return new DoctorStats(id, rs.getString("full_name"), rs.getString("specialty"), rs.getLong("booked"),
                    rs.getLong("completed"), rs.getLong("no_shows"), round(booked), round(available),
                    available == 0 ? 0 : round(100.0 * booked / available),
                    nullableRound(rs.getObject("avg_consult")), nullableRound(rs.getObject("avg_wait")));
        });
    }

    @Transactional(readOnly = true)
    public Revenue revenue(LocalDate from, LocalDate to) {
        String sql = """
                select d.full_name as doctor, s.name as specialty, coalesce(p.mode, 'UNKNOWN') as mode,
                       sum(p.amount - p.refunded_amount) as net
                from payment p
                join appointment a on a.id = p.appointment_id
                join doctor d on d.id = a.doctor_id
                join specialty s on s.id = d.specialty_id
                where p.deleted = false and p.status in ('SUCCEEDED','REFUNDED','PARTIALLY_REFUNDED')
                  and p.paid_at >= :from and p.paid_at < :to
                group by d.full_name, s.name, p.mode""";
        Map<String, BigDecimal> byDoctor = new TreeMap<>();
        Map<String, BigDecimal> bySpecialty = new TreeMap<>();
        Map<String, BigDecimal> byMode = new TreeMap<>();
        BigDecimal[] total = {BigDecimal.ZERO};
        jdbc.query(sql, range(from, to, clinicService.zone()), rs -> {
            BigDecimal net = rs.getBigDecimal("net");
            byDoctor.merge(rs.getString("doctor"), net, BigDecimal::add);
            bySpecialty.merge(rs.getString("specialty"), net, BigDecimal::add);
            byMode.merge(rs.getString("mode"), net, BigDecimal::add);
            total[0] = total[0].add(net);
        });
        return new Revenue(total[0], byDoctor, bySpecialty, byMode);
    }

    @Transactional(readOnly = true)
    public PatientStats patients(LocalDate from, LocalDate to) {
        String sql = """
                with firsts as (
                  select patient_id, min(start_at) as first_at from appointment
                  where deleted = false and status <> 'EXPIRED' group by patient_id)
                select count(distinct a.patient_id) filter (where f.first_at >= :from) as new_patients,
                       count(distinct a.patient_id) filter (where f.first_at < :from) as returning_patients,
                       count(*) as total,
                       count(*) filter (where a.status = 'CANCELLED') as cancelled,
                       count(*) filter (where a.status = 'NO_SHOW') as no_shows
                from appointment a join firsts f on f.patient_id = a.patient_id
                where a.deleted = false and a.status <> 'EXPIRED' and a.start_at >= :from and a.start_at < :to""";
        return jdbc.queryForObject(sql, range(from, to, clinicService.zone()), (rs, i) -> {
            long total = rs.getLong("total");
            long cancelled = rs.getLong("cancelled");
            long noShows = rs.getLong("no_shows");
            return new PatientStats(rs.getLong("new_patients"), rs.getLong("returning_patients"), total, cancelled, noShows,
                    total == 0 ? 0 : round(100.0 * cancelled / total), total == 0 ? 0 : round(100.0 * noShows / total));
        });
    }

    @Transactional(readOnly = true)
    public List<HeatCell> heatmap(LocalDate from, LocalDate to) {
        ZoneId zone = clinicService.zone();
        String sql = """
                select cast(extract(isodow from a.start_at at time zone :tz) as int) as dow,
                       cast(extract(hour from a.start_at at time zone :tz) as int) as hr,
                       count(*) as c
                from appointment a
                where a.deleted = false and a.status not in ('CANCELLED','EXPIRED')
                  and a.start_at >= :from and a.start_at < :to
                group by 1, 2 order by 1, 2""";
        return jdbc.query(sql, range(from, to, zone).addValue("tz", zone.getId()),
                (rs, i) -> new HeatCell(rs.getInt("dow"), rs.getInt("hr"), rs.getLong("c")));
    }

    /** CSV of appointments and revenue. Deliberately excludes patient names and all clinical data. */
    @Transactional(readOnly = true)
    public String exportCsv(LocalDate from, LocalDate to) {
        ZoneId zone = clinicService.zone();
        String sql = """
                select a.id, a.start_at, d.full_name as doctor, s.name as specialty, t.name as type, a.mode, a.status,
                       a.source, a.payment_mode, p.amount, p.refunded_amount, p.status as pay_status, p.mode as pay_method
                from appointment a
                join doctor d on d.id = a.doctor_id
                join specialty s on s.id = d.specialty_id
                join appointment_type t on t.id = a.appointment_type_id
                left join lateral (select * from payment p where p.appointment_id = a.id order by p.created_at desc limit 1) p on true
                where a.deleted = false and a.start_at >= :from and a.start_at < :to
                order by a.start_at""";
        StringBuilder sb = new StringBuilder(
                "appointment_id,start_local,doctor,specialty,type,mode,status,source,payment_mode,amount,refunded,payment_status,payment_method\n");
        jdbc.query(sql, range(from, to, zone), rs -> {
            sb.append(csv(rs.getString("id"))).append(',')
                    .append(csv(rs.getTimestamp("start_at").toInstant().atZone(zone).toLocalDateTime().toString())).append(',')
                    .append(csv(rs.getString("doctor"))).append(',')
                    .append(csv(rs.getString("specialty"))).append(',')
                    .append(csv(rs.getString("type"))).append(',')
                    .append(csv(rs.getString("mode"))).append(',')
                    .append(csv(rs.getString("status"))).append(',')
                    .append(csv(rs.getString("source"))).append(',')
                    .append(csv(rs.getString("payment_mode"))).append(',')
                    .append(csv(rs.getString("amount"))).append(',')
                    .append(csv(rs.getString("refunded_amount"))).append(',')
                    .append(csv(rs.getString("pay_status"))).append(',')
                    .append(csv(rs.getString("pay_method"))).append('\n');
        });
        return sb.toString();
    }

    private double sessionMinutes(UUID doctorId, LocalDate from, LocalDate to) {
        List<DoctorSession> list = sessions.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId);
        double total = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (clinicService.openWindow(d).isEmpty()) {
                continue;
            }
            for (DoctorSession s : list) {
                if (s.getDayOfWeek() == d.getDayOfWeek()) {
                    total += Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
                }
            }
        }
        return total;
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to, ZoneId zone) {
        return new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from.atStartOfDay(zone).toInstant()))
                .addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()));
    }

    /** Neutralises spreadsheet formula injection and quotes as needed. */
    static String csv(String v) {
        if (v == null) {
            return "";
        }
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double nullableRound(Object o) {
        return o == null ? null : round(((Number) o).doubleValue());
    }

    public Range defaultRange(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(clinicService.zone()));
        return new Range(from == null ? today.minusDays(29) : from, to == null ? today : to);
    }
}
