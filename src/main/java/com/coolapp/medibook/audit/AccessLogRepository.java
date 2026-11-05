package com.coolapp.medibook.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface AccessLogRepository extends JpaRepository<AccessLog, UUID> {
    Page<AccessLog> findByPatientIdInOrderByCreatedAtDesc(Collection<UUID> patientIds, Pageable pageable);
}
