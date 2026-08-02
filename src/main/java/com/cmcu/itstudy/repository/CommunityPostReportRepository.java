package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityPostReportRepository extends JpaRepository<CommunityPostReport, UUID> {

    boolean existsByPostIdAndReporterId(UUID postId, UUID reporterUserId);

    Optional<CommunityPostReport> findByPostIdAndReporterId(UUID postId, UUID reporterUserId);

    List<CommunityPostReport> findByPostId(UUID postId);

    @Query("SELECT r FROM CommunityPostReport r WHERE r.status = :status AND (r.post.deleted = false OR r.post.deleted IS NULL) ORDER BY r.createdAt DESC")
    Page<CommunityPostReport> findByStatusAndPostDeletedFalse(@Param("status") String status, Pageable pageable);

    @Query("SELECT r FROM CommunityPostReport r WHERE (r.post.deleted = false OR r.post.deleted IS NULL) ORDER BY r.createdAt DESC")
    Page<CommunityPostReport> findByPostDeletedFalse(Pageable pageable);

    Page<CommunityPostReport> findAllByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<CommunityPostReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByPostId(UUID postId);

    boolean existsByPostIdAndStatus(UUID postId, String status);
}
