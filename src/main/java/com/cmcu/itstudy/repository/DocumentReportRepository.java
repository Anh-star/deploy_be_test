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
           "ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM DocumentReport r WHERE (:status IS NULL OR :status = '' OR r.status = :status)")
    Page<DocumentReport> searchReports(@Param("status") String status, Pageable pageable);

    @Query("SELECT COUNT(r) FROM DocumentReport r WHERE r.document.id = :documentId")
    long countByDocumentId(@Param("documentId") UUID documentId);

    boolean existsByDocumentIdAndStatus(UUID documentId, String status);
}
