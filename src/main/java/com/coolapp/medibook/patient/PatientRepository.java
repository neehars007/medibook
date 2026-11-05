package com.coolapp.medibook.patient;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByUserId(UUID userId);

    List<Patient> findByGuardianPatientId(UUID guardianPatientId);

    @Query("select p from Patient p where lower(p.fullName) like lower(concat('%', :q, '%')) or p.phone like concat('%', :q, '%') order by p.fullName")
    List<Patient> searchByNameOrPhone(@Param("q") String q, Pageable pageable);

    List<Patient> findByDob(LocalDate dob);
}
