package com.coolapp.medibook.clinic;

import com.coolapp.medibook.audit.AuditService;
import com.coolapp.medibook.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClinicService {

    private final ClinicRepository clinics;
    private final ClinicHoursRepository hours;
    private final HolidayRepository holidays;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Clinic get() {
        return clinics.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Clinic is not configured yet"));
    }

    public ZoneId zone() {
        return get().zone();
    }

    public Optional<ClinicHours> hoursFor(DayOfWeek day) {
        return hours.findByDayOfWeek(day);
    }

    public boolean isHoliday(LocalDate date) {
        return holidays.existsByDate(date);
    }

    public List<ClinicHours> allHours() {
        return hours.findAll().stream().sorted((a, b) -> a.getDayOfWeek().compareTo(b.getDayOfWeek())).toList();
    }

    public List<Holiday> upcomingHolidays(LocalDate from) {
        return holidays.findByDateGreaterThanEqualOrderByDateAsc(from);
    }

    @Transactional
    public Clinic updateProfile(ClinicDtos.ClinicProfileRequest req) {
        Clinic c = clinics.findFirstByOrderByCreatedAtAsc().orElseGet(Clinic::new);
        c.setName(req.name());
        c.setAddress(req.address());
        c.setPhone(req.phone());
        c.setTimezone(ZoneId.of(req.timezone()).getId());
        c.setLogoUrl(req.logoUrl());
        c.setAbout(req.about());
        c.setEmergencyNotice(req.emergencyNotice());
        clinics.save(c);
        audit.log("CLINIC_UPDATED", "Clinic", c.getId(), null);
        return c;
    }

    @Transactional
    public Clinic updatePolicy(ClinicDtos.ClinicPolicyRequest req) {
        Clinic c = get();
        c.setMinNoticeMinutes(req.minNoticeMinutes());
        c.setMaxAdvanceDays(req.maxAdvanceDays());
        c.setCancellationCutoffHours(req.cancellationCutoffHours());
        c.setPaymentPolicy(req.paymentPolicy());
        c.setNoShowPrepayThreshold(req.noShowPrepayThreshold());
        c.setBufferMinutes(req.bufferMinutes());
        c.setSlotGranularityMinutes(req.slotGranularityMinutes());
        audit.log("CLINIC_POLICY_UPDATED", "Clinic", c.getId(), req.toString());
        return c;
    }

    @Transactional
    public List<ClinicHours> updateHours(List<ClinicDtos.HoursRequest> req) {
        for (ClinicDtos.HoursRequest r : req) {
            if (!r.closed() && (r.openTime() == null || r.closeTime() == null || !r.openTime().isBefore(r.closeTime()))) {
                throw ApiException.badRequest("Invalid hours for " + r.dayOfWeek());
            }
            ClinicHours h = hours.findByDayOfWeek(r.dayOfWeek()).orElseGet(ClinicHours::new);
            h.setDayOfWeek(r.dayOfWeek());
            h.setClosed(r.closed());
            h.setOpenTime(r.closed() ? null : r.openTime());
            h.setCloseTime(r.closed() ? null : r.closeTime());
            hours.save(h);
        }
        audit.log("CLINIC_HOURS_UPDATED", "ClinicHours", null, null);
        return allHours();
    }

    @Transactional
    public Holiday addHoliday(LocalDate date, String name) {
        if (holidays.existsByDate(date)) {
            throw ApiException.conflict("Holiday already exists on " + date);
        }
        Holiday h = new Holiday();
        h.setDate(date);
        h.setName(name);
        holidays.save(h);
        audit.log("HOLIDAY_ADDED", "Holiday", h.getId(), date.toString());
        return h;
    }

    @Transactional
    public void deleteHoliday(UUID id) {
        Holiday h = holidays.findById(id).orElseThrow(() -> ApiException.notFound("Holiday"));
        h.setDeleted(true);
        audit.log("HOLIDAY_DELETED", "Holiday", id, null);
    }

    /** Opening window for a date, or empty if closed/holiday. */
    public Optional<LocalTime[]> openWindow(LocalDate date) {
        if (isHoliday(date)) {
            return Optional.empty();
        }
        return hoursFor(date.getDayOfWeek())
                .filter(h -> !h.isClosed() && h.getOpenTime() != null && h.getCloseTime() != null)
                .map(h -> new LocalTime[]{h.getOpenTime(), h.getCloseTime()});
    }
}
