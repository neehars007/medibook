package com.coolapp.medibook.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID>, JpaSpecificationExecutor<Doctor> {
    Optional<Doctor> findByUserId(UUID userId);

    List<Doctor> findBySpecialtyIdAndActiveTrue(UUID specialtyId);

    List<Doctor> findByActiveTrue();
}
