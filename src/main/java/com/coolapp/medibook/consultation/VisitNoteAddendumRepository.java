package com.coolapp.medibook.consultation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VisitNoteAddendumRepository extends JpaRepository<VisitNoteAddendum, UUID> {
    List<VisitNoteAddendum> findByVisitNoteIdOrderByCreatedAtAsc(UUID visitNoteId);
}
