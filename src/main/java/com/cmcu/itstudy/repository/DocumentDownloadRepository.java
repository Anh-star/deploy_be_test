package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentDownload;
import com.cmcu.itstudy.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DocumentDownloadRepository extends JpaRepository<DocumentDownload, Long> {

    long countByDocument(Document document);

    long countByDocument_Id(UUID documentId);

    boolean existsByDocument_IdAndUser_Id(UUID documentId, UUID userId);

    default long countByDocumentId(UUID documentId) {
        return countByDocument_Id(documentId);
    }

    long countByDocumentAndDownloadedAtAfter(Document document, LocalDateTime from);

    @Query("""
            select count(d)
            from DocumentDownload d
            where d.document = :document
              and d.downloadedAt >= :from
            """)
    long countRecentDownloads(@Param("document") Document document, @Param("from") LocalDateTime from);
}
