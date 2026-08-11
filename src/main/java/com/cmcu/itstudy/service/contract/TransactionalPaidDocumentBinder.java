package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.storage.StorageObjectInfo;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional binder for the PAID {@code /api/my-documents} POST flow.
 *
 * <p>This service runs in its own database transaction
 * ({@code @Transactional(REQUIRED)}). It owns the atomic flip of:
 * <ul>
 *   <li>a freshly inserted {@link com.cmcu.itstudy.entity.Document}</li>
 *   <li>a freshly inserted {@link com.cmcu.itstudy.entity.DocumentFile}
 *       (fileUrl = null; storage path copied from
 *       {@link com.cmcu.itstudy.entity.PendingStorageUpload})</li>
 *   <li>the {@link com.cmcu.itstudy.entity.PendingStorageUpload} row
 *       from {@code PENDING} to {@code BOUND} via
 *       {@link com.cmcu.itstudy.repository.custom.PendingStorageUploadClaimRepository#bindPendingUpload}
 *       (which is {@code @Transactional(MANDATORY)} and verifies the
 *       race in SQL Server).</li>
 * </ul>
 *
 * <p>The three writes commit or rollback together. There is no remote
 * Supabase call inside this method. The verified
 * {@link com.cmcu.itstudy.dto.storage.StorageObjectInfo} is supplied
 * by the orchestrator after it has already read the byte-by-byte
 * payload from Supabase outside any DB transaction.
 *
 * <p>Concurrency guarantees:
 * <ul>
 *   <li>Only one paid-create request with a given {@code uploadId} can
 *       succeed. The other request(s) get
 *       {@link com.cmcu.itstudy.handle.PendingUploadBindConflictException}.</li>
 *   <li>A paid request referencing a foreign user, an expired pending
 *       row, or an already-bound pending row is rejected with HTTP 409
 *       and the transaction rolls back. No Document / DocumentFile rows
 *       are persisted in any failure case.</li>
 * </ul>
 */
public interface TransactionalPaidDocumentBinder {

    /**
     * Atomically bind a verified paid-object to a freshly created
     * {@code Document} and {@code DocumentFile} row, flip the pending
     * upload to {@code BOUND}, and return the mapped response DTO.
     *
     * <p>The {@code Document}, {@code DocumentFile}, and {@code Pending}
     * updates commit or rollback as a single atomic unit. There is NO
     * remote Supabase call inside this method.
     *
     * @param metadata       validated create payload (PAID shape)
     * @param uploadId       the {@code uploadId} returned by
     *                       {@code POST /storage/paid-upload-target}
     * @param currentUserId  authenticated user id
     * @param verified       storage object info already verified against
     *                       the {@code PendingStorageUpload} expectations
     *                       (size, MIME)
     * @param now            single "now" supplied by the orchestrator
     * @return mapped card DTO for the persisted document
     */
    DocumentCardDto bindPaidCreate(
            DocumentCreateRequestDto metadata,
            UUID uploadId,
            UUID currentUserId,
            StorageObjectInfo verified,
            LocalDateTime now);
}
