package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.PendingStorageUpload;
import com.cmcu.itstudy.entity.StorageCleanupTask;
import com.cmcu.itstudy.enums.StorageCleanupReason;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.PendingStorageUploadRepository;
import com.cmcu.itstudy.repository.StorageCleanupTaskRepository;
import com.cmcu.itstudy.service.contract.StorageCleanupTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence-only operations for the storage cleanup task queue.
 *
 * <p>This service does NOT perform any remote storage calls. It only writes
 * to {@code dbo.tbl_storage_cleanup_tasks} so the remote cleanup worker
 * (implemented in a later stage) can pick them up.
 *
 * <h2>Transaction propagation</h2>
 * <ul>
 *   <li>{@link #enqueueNewObjectCleanup}: REQUIRES_NEW. The new-object
 *       cleanup task must commit independently of the failed bind/update
 *       transaction that triggered the enqueue.</li>
 *   <li>{@link #enqueueOldObjectCleanup}: MANDATORY. The old-object cleanup
 *       task MUST commit atomically with the file-replacement transaction so
 *       that a rollback of the replacement also rolls back the cleanup
 *       enqueue (avoiding orphan old objects).</li>
 *   <li>{@link #markDone} / {@link #markRetry} / {@link #markDead} /
 *       {@link #recoverStaleInProgress}: REQUIRED. Callers in a normal flow
 *       do not wrap these in an outer transaction.</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * <p>The service uses a conditional native INSERT under
 * {@code UPDLOCK + HOLDLOCK} to provide best-effort serialized dedup.
 * Authoritative dedup is the filtered unique index
 * {@code UX_storage_cleanup_tasks_active_unique} created out-of-band by the
 * operator. Until that index exists, the {@code UPDLOCK + HOLDLOCK}
 * conditional insert provides best-effort serialized insert behavior.
 * The filtered unique index MUST still be created manually.
 *
 * <p>This service never catches
 * {@link org.springframework.dao.DataIntegrityViolationException} and
 * continues to query in the same transaction. The conditional insert either
 * succeeds and returns the inserted id, or returns empty and the caller
 * performs a clean lookup in the same transaction.
 *
 * <h2>Repository injection</h2>
 * <p>This service injects ONLY the root Spring Data repositories
 * ({@link StorageCleanupTaskRepository} and {@link PendingStorageUploadRepository}).
 * The custom fragment interfaces
 * ({@code StorageCleanupTaskClaimRepository},
 * {@code StorageCleanupTaskInsertRepository},
 * {@code PendingStorageUploadClaimRepository}) are NOT injected directly.
 * Per Spring Data fragment architecture, the fragment implementations are
 * composed into the root repository by Spring Data and must NOT be picked
 * up by component scan as standalone beans.
 */
@Service
public class StorageCleanupTaskServiceImpl implements StorageCleanupTaskService {

    private final StorageCleanupTaskRepository cleanupTaskRepository;
    private final PendingStorageUploadRepository pendingUploadRepository;
    private final DocumentRepository documentRepository;

    public StorageCleanupTaskServiceImpl(
            StorageCleanupTaskRepository cleanupTaskRepository,
            PendingStorageUploadRepository pendingUploadRepository,
            DocumentRepository documentRepository) {
        this.cleanupTaskRepository = cleanupTaskRepository;
        this.pendingUploadRepository = pendingUploadRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StorageCleanupTask enqueueNewObjectCleanup(
            String targetBucket,
            String targetPath,
            StorageCleanupReason reason,
            UUID pendingUploadId,
            UUID documentId) {

        return conditionalInsertAndResolve(
                targetBucket, targetPath, reason, pendingUploadId, documentId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public StorageCleanupTask enqueueNewObjectCleanupInCurrentTransaction(
            String targetBucket,
            String targetPath,
            StorageCleanupReason reason,
            UUID pendingUploadId,
            UUID documentId) {

        // MANDATORY propagation guarantees the Spring proxy throws if no
        // caller transaction is active, so the failure-mode helper can
        // safely rely on a single enclosing transaction.
        return conditionalInsertAndResolve(
                targetBucket, targetPath, reason, pendingUploadId, documentId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public StorageCleanupTask enqueueOldObjectCleanup(
            String targetBucket,
            String targetPath,
            StorageCleanupReason reason,
            UUID documentId) {

        return conditionalInsertAndResolve(
                targetBucket, targetPath, reason, null, documentId);
    }

    /**
     * Performs the conditional insert and resolves to the operating task row.
     * Runs in the caller's transaction. No exception is caught to continue
     * querying in the same transaction.
     */
    private StorageCleanupTask conditionalInsertAndResolve(
            String targetBucket,
            String targetPath,
            StorageCleanupReason reason,
            UUID pendingUploadId,
            UUID documentId) {

        LocalDateTime now = LocalDateTime.now();
        Optional<Long> insertedId = cleanupTaskRepository.insertActiveTaskIfAbsent(
                targetBucket, targetPath, reason,
                pendingUploadId, documentId,
                now, now);

        if (insertedId.isPresent()) {
            Long id = insertedId.get();
            StorageCleanupTask task = cleanupTaskRepository.findById(id).orElseThrow(
                    () -> new IllegalStateException(
                            "Inserted cleanup task id=" + id + " not found"));
            attachRelations(task, pendingUploadId, documentId);
            return cleanupTaskRepository.saveAndFlush(task);
        }

        return cleanupTaskRepository.findActiveTask(targetBucket, targetPath, reason)
                .orElseThrow(() -> new IllegalStateException(
                        "Conditional insert returned empty but no active task "
                                + "was found for bucket=" + targetBucket
                                + " path=" + targetPath
                                + " reason=" + reason));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public StorageCleanupTask markDone(Long taskId, String lastErrorOrNull) {
        boolean ok = cleanupTaskRepository.markDone(taskId, lastErrorOrNull);
        if (!ok) {
            throw new IllegalStateException(
                    "Storage cleanup task id=" + taskId + " not in IN_PROGRESS");
        }
        return cleanupTaskRepository.findById(taskId).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public StorageCleanupTask markRetry(
            Long taskId,
            LocalDateTime nextRetryAt,
            String lastError) {

        boolean ok = cleanupTaskRepository.markRetry(taskId, nextRetryAt, lastError);
        if (!ok) {
            throw new IllegalStateException(
                    "Storage cleanup task id=" + taskId + " not in IN_PROGRESS");
        }
        return cleanupTaskRepository.findById(taskId).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public StorageCleanupTask markDead(Long taskId, String lastError) {
        boolean ok = cleanupTaskRepository.markDead(taskId, lastError);
        if (!ok) {
            throw new IllegalStateException(
                    "Storage cleanup task id=" + taskId + " not claimable");
        }
        return cleanupTaskRepository.findById(taskId).orElseThrow();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public int recoverStaleInProgress(LocalDateTime staleBefore, String lastError) {
        return cleanupTaskRepository.recoverStaleInProgress(staleBefore, lastError);
    }

    private void attachRelations(
            StorageCleanupTask task,
            UUID pendingUploadId,
            UUID documentId) {

        if (pendingUploadId != null && task.getPendingUpload() == null) {
            PendingStorageUpload pending =
                    pendingUploadRepository.findByUploadId(pendingUploadId).orElse(null);
            if (pending != null) {
                task.setPendingUpload(pending);
            }
        }
        if (documentId != null && task.getDocument() == null) {
            Document document = documentRepository.findById(documentId).orElse(null);
            if (document != null) {
                task.setDocument(document);
            }
        }
    }
}