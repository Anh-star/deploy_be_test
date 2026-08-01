package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityPostReportRepository extends JpaRepository<CommunityPostReport, UUID> {

    boolean existsByPostIdAndReporterId(UUID postId, UUID reporterUserId);

    Optional<CommunityPostReport> findByPostIdAndReporterId(UUID postId, UUID reporterUserId);

    Page<CommunityPostReport> findAllByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<CommunityPostReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByPostId(UUID postId);

    boolean existsByPostIdAndStatus(UUID postId, String status);
}
