package com.coolapp.medibook.appointment;

import com.coolapp.medibook.appointment.AppointmentDtos.AppointmentDto;
import com.coolapp.medibook.consultation.VisitNote;
import com.coolapp.medibook.consultation.VisitNoteRepository;
import com.coolapp.medibook.doctor.Doctor;
import com.coolapp.medibook.doctor.DoctorRepository;
import com.coolapp.medibook.patient.Patient;
import com.coolapp.medibook.patient.PatientRepository;
import com.coolapp.medibook.payment.PaymentRepository;
import com.coolapp.medibook.room.Room;
import com.coolapp.medibook.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds appointment DTOs with names resolved in bulk. Contains no clinical data. */
@Component
@RequiredArgsConstructor
public class AppointmentViews {

    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final AppointmentTypeRepository types;
    private final RoomRepository rooms;
    private final PaymentRepository payments;
    private final VisitNoteRepository notes;

    public AppointmentDto one(Appointment a) {
        return many(List.of(a)).get(0);
    }

    public List<AppointmentDto> many(List<Appointment> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, Doctor> d = byId(doctors.findAllById(ids(list, Appointment::getDoctorId)), Doctor::getId);
        Map<UUID, Patient> p = byId(patients.findAllById(ids(list, Appointment::getPatientId)), Patient::getId);
        Map<UUID, AppointmentType> t = byId(types.findAllById(ids(list, Appointment::getAppointmentTypeId)), AppointmentType::getId);
        Map<UUID, Room> r = byId(rooms.findAllById(ids(list, Appointment::getRoomId)), Room::getId);
        Set<UUID> withSummary = notes.findByAppointmentIdInAndFinalizedAtIsNotNull(ids(list, Appointment::getId))
                .stream().map(VisitNote::getAppointmentId).collect(Collectors.toSet());
        return list.stream().map(a -> new AppointmentDto(
                a.getId(), a.getPatientId(),
                p.containsKey(a.getPatientId()) ? p.get(a.getPatientId()).getFullName() : null,
                a.getDoctorId(), d.containsKey(a.getDoctorId()) ? d.get(a.getDoctorId()).getFullName() : null,
                a.getAppointmentTypeId(), t.containsKey(a.getAppointmentTypeId()) ? t.get(a.getAppointmentTypeId()).getName() : null,
                a.getStartAt(), a.getEndAt(), a.getMode(), a.getStatus(), a.getSource(), a.getTokenNumber(),
                a.getPaymentMode(),
                payments.findFirstByAppointmentIdOrderByCreatedAtDesc(a.getId()).map(x -> x.getStatus().name()).orElse(null),
                a.getCancelledBy(), a.getCancelReason(),
                a.getRoomId() != null && r.containsKey(a.getRoomId()) ? r.get(a.getRoomId()).getName() : null,
                a.getCheckedInAt(), withSummary.contains(a.getId()))).toList();
    }

    private static Set<UUID> ids(List<Appointment> list, Function<Appointment, UUID> f) {
        return list.stream().map(f).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<UUID, T> byId(List<T> items, Function<T, UUID> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
