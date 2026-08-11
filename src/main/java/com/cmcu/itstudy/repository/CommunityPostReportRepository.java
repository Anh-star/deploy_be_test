package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("SELECT r FROM CommunityPostReport r LEFT JOIN FETCH r.reporter WHERE r.post.id = :postId")
    List<CommunityPostReport> findByPostId(@Param("postId") UUID postId);

    @Modifying
    @Query("UPDATE CommunityPostReport r SET r.status = :status WHERE r.post.id = :postId")
    int updateStatusByPostId(@Param("postId") UUID postId, @Param("status") String status);

    @Modifying
    @Query("UPDATE CommunityPostReport r SET r.status = :status WHERE r.id = :reportId")
    int updateStatusByReportId(@Param("reportId") UUID reportId, @Param("status") String status);

    @Query("SELECT r FROM CommunityPostReport r WHERE r.status = :status AND (r.post.deleted = false OR r.post.deleted IS NULL) ORDER BY r.createdAt DESC")
    Page<CommunityPostReport> findByStatusAndPostDeletedFalse(@Param("status") String status, Pageable pageable);

    @Query("SELECT r FROM CommunityPostReport r WHERE (r.post.deleted = false OR r.post.deleted IS NULL) ORDER BY r.createdAt DESC")
    Page<CommunityPostReport> findByPostDeletedFalse(Pageable pageable);



    @Query("SELECT COUNT(r) FROM CommunityPostReport r WHERE r.post.id = :postId")
    long countByPostId(@Param("postId") UUID postId);

    @Query("SELECT COUNT(r) > 0 FROM CommunityPostReport r WHERE r.post.id = :postId AND r.status = :status")
    boolean existsByPostIdAndStatus(@Param("postId") UUID postId, @Param("status") String status);

    @Query("SELECT COUNT(DISTINCT r.post.id) FROM CommunityPostReport r WHERE r.status = :status")
    long countDistinctPostByStatus(@Param("status") String status);

    @Query("SELECT COUNT(r) FROM CommunityPostReport r WHERE r.status = :status")
    long countByStatusAndPostDeletedFalse(@Param("status") String status);

    @Query("SELECT COUNT(DISTINCT r.post.id) FROM CommunityPostReport r WHERE r.post.hidden = true AND (r.post.deleted = false OR r.post.deleted IS NULL)")
    long countDistinctHiddenReportedPosts();

    @Query("SELECT r FROM CommunityPostReport r " +
           "LEFT JOIN r.post p " +
           "LEFT JOIN p.author a " +
           "LEFT JOIN r.reporter rep " +
           "WHERE (:status IS NULL OR :status = '' OR r.status = :status) " +
           "AND (:keyword IS NULL OR :keyword = '' OR " +
           "     LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(a.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(rep.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:startDate IS NULL OR r.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR r.createdAt <= :endDate) " +
           "ORDER BY r.createdAt DESC")
    Page<CommunityPostReport> searchReports(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            Pageable pageable
    );
}
