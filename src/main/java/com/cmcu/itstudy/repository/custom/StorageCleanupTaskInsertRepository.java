package com.cmcu.itstudy.repository.custom;

import com.cmcu.itstudy.enums.StorageCleanupReason;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Conditional insert for cleanup tasks.
 *
 * <p>Authoritative DB-level dedup is the filtered unique index created
 * out-of-band by the operator. This repository provides a best-effort
 * serialized insert that avoids depending on a constraint violation to
 * discover duplicates.
 *
 * <p>The insert runs inside the caller's transaction (no transaction
 * boundary declared here). Callers in {@link com.cmcu.itstudy.service.impl
 * .StorageCleanupTaskServiceImpl} choose the appropriate propagation.
 */
public interface StorageCleanupTaskInsertRepository {

    /**
     * Inserts a new cleanup task only if no active equivalent task already
     * exists.
     *
     * @return Optional containing the inserted id when a new row was created;
     *         Optional.empty when an active equivalent task already exists.
     */
    Optional<Long> insertActiveTaskIfAbsent(
            String targetBucket,
            String targetPath,
            StorageCleanupReason reason,
            UUID pendingUploadId,
            UUID documentId,
            LocalDateTime nextRetryAt,
            LocalDateTime now
    );
}