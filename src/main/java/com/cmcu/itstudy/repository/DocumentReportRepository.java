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

    @Query("SELECT r FROM DocumentReport r WHERE r.status = :status AND (r.document.deleted = false OR r.document.deleted IS NULL) ORDER BY r.createdAt DESC")
    Page<DocumentReport> findByStatusAndDocumentDeletedFalse(@Param("status") String status, Pageable pageable);

    @Query("SELECT r FROM DocumentReport r WHERE (r.document.deleted = false OR r.document.deleted IS NULL) ORDER BY r.createdAt DESC")
    Page<DocumentReport> findByDocumentDeletedFalse(Pageable pageable);

    Page<DocumentReport> findAllByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<DocumentReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByDocumentId(UUID documentId);

    boolean existsByDocumentIdAndStatus(UUID documentId, String status);
}
