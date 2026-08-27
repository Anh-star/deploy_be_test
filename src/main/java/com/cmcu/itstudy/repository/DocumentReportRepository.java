package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentReport;
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
public interface DocumentReportRepository extends JpaRepository<DocumentReport, UUID> {

    boolean existsByDocumentIdAndReporterId(UUID documentId, UUID reporterUserId);

    Optional<DocumentReport> findByDocumentIdAndReporterId(UUID documentId, UUID reporterUserId);

    List<DocumentReport> findByDocumentId(UUID documentId);

    @Query(value = "SELECT r FROM DocumentReport r " +
           "LEFT JOIN r.document d " +
           "LEFT JOIN r.reporter rep " +
           "WHERE (:status IS NULL OR :status = '' OR r.status = :status) " +
           "AND (:search IS NULL OR :search = '' OR LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR (rep.fullName IS NOT NULL AND LOWER(rep.fullName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "     OR (r.detail IS NOT NULL AND LOWER(r.detail) LIKE LOWER(CONCAT('%', :search, '%')))) " +
           "AND (:startDate IS NULL OR r.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR r.createdAt <= :endDate) " +
           "ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM DocumentReport r " +
           "LEFT JOIN r.document d " +
           "LEFT JOIN r.reporter rep " +
           "WHERE (:status IS NULL OR :status = '' OR r.status = :status) " +
           "AND (:search IS NULL OR :search = '' OR LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR (rep.fullName IS NOT NULL AND LOWER(rep.fullName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "     OR (r.detail IS NOT NULL AND LOWER(r.detail) LIKE LOWER(CONCAT('%', :search, '%')))) " +
           "AND (:startDate IS NULL OR r.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR r.createdAt <= :endDate)")
    Page<DocumentReport> searchReports(
            @Param("status") String status,
            @Param("search") String search,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            Pageable pageable
    );

    default Page<DocumentReport> searchReports(String status, Pageable pageable) {
        return searchReports(status, null, null, null, pageable);
    }

    long countByStatus(String status);

    @Query("SELECT COUNT(r) FROM DocumentReport r WHERE r.document.id = :documentId")
    long countByDocumentId(@Param("documentId") UUID documentId);

    boolean existsByDocumentIdAndStatus(UUID documentId, String status);
}
