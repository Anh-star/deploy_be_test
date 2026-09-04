package com.cmcu.itstudy.scheduler;

import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.service.contract.SupabaseStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class DocumentRetentionCleanupScheduler {

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final SupabaseStorageService supabaseStorageService;

    public DocumentRetentionCleanupScheduler(
            DocumentRepository documentRepository,
            DocumentFileRepository documentFileRepository,
            SupabaseStorageService supabaseStorageService
    ) {
        this.documentRepository = documentRepository;
        this.documentFileRepository = documentFileRepository;
        this.supabaseStorageService = supabaseStorageService;
    }

    /**
     * Runs daily at 02:00:00 AM to clean up cloud storage files of documents
     * whose retention period has expired.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void executeRetentionCleanup() {
        log.info("Starting scheduled document retention cleanup task...");
        LocalDateTime now = LocalDateTime.now();

        try {
            List<Document> expiredDocs = documentRepository
                    .findByDeletedTrueAndRetentionExpiresAtBeforeAndFileCleanedFalse(now);

            if (expiredDocs.isEmpty()) {
                log.info("No expired documents pending file cleanup.");
                return;
            }

            log.info("Found {} expired documents with files pending cleanup.", expiredDocs.size());

            int cleanedCount = 0;
            for (Document doc : expiredDocs) {
                try {
                    List<DocumentFile> files = documentFileRepository.findAllByDocument_Id(doc.getId());
                    for (DocumentFile file : files) {
                        if (file.getStorageBucket() != null && file.getStoragePath() != null) {
                            supabaseStorageService.deleteObject(file.getStorageBucket(), file.getStoragePath());
                        }
                    }

                    doc.setFileCleaned(true);
                    documentRepository.save(doc);
                    cleanedCount++;
                } catch (Exception docEx) {
                    log.error("Failed to clean up files for document id={}: {}", doc.getId(), docEx.getMessage());
                }
            }

            log.info("Completed document retention cleanup: cleaned files for {}/{} documents.", cleanedCount, expiredDocs.size());
        } catch (Exception e) {
            log.error("Error during scheduled document retention cleanup task", e);
        }
    }
}
