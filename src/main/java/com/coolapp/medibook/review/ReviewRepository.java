package com.coolapp.medibook.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    boolean existsByAppointmentId(UUID appointmentId);

    Page<Review> findByDoctorIdAndStatusOrderByCreatedAtDesc(UUID doctorId, ReviewStatus status, Pageable pageable);

    Page<Review> findByStatusOrderByCreatedAtAsc(ReviewStatus status, Pageable pageable);

    @Query("select avg(r.rating) from Review r where r.doctorId = :doctorId and r.status = com.coolapp.medibook.review.ReviewStatus.APPROVED")
    Double averageApproved(@Param("doctorId") UUID doctorId);

    long countByDoctorIdAndStatus(UUID doctorId, ReviewStatus status);
}
