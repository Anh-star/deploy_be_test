package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.entity.User;

import java.util.UUID;

/**
 * Coordinator for the PAID {@code /api/my-documents} POST flow.
 *
 * <p>The orchestrator is the single boundary that:
 * <ol>
 *   <li>loads the {@code PendingStorageUpload} snapshot and verifies
 *       pre-conditions (owner, status, expiry, bucket, path),</li>
 *   <li>issues the Supabase object-info HTTP call (which MUST run
 *       outside any database transaction), and</li>
 *   <li>delegates the atomic Document + DocumentFile + Pending bind
 *       to {@link TransactionalPaidDocumentBinder}.</li>
 * </ol>
 *
 * <p>The orchestrator is annotated {@code @Transactional(NOT_SUPPORTED)}
 * so that step 2 is never inside a database transaction. The binder's
 * own {@code @Transactional(REQUIRED)} opens the only transaction in
 * the paid flow.
 *
 * <p>The orchestrator is also responsible for source-consistent failure
 * cleanup when the uploaded bytes do not match the declared metadata:
 * <ul>
 *   <li>size or MIME mismatch → short in-memory transaction marks the
 *       pending row {@code CANCELED} and enqueues a
 *       {@code BIND_FAIL_NEW} cleanup task so the remote object is
 *       deleted eventually (scheduler not implemented in Phase C1).</li>
 *   <li>expiry → mark {@code CLEANING} so the cleanup scheduler can
 *       delete the remote object with {@code EXPIRED_PENDING_UPLOAD}.</li>
 *   <li>object missing → leave pending {@code PENDING} so the upload
 *       remains a valid retry surface until expiration.</li>
 * </ul>
 *
 * <p>This service never logs the token, the signed URL, the
 * {@code uploadId}, or any payload.
 */
public interface PaidDocumentUploadOrchestrator {

    /**
     * Run the paid-document create flow end-to-end.
     *
     * <p>Steps in order (each with its own database-transaction or no
     * transaction as documented):
     * <ol>
     *   <li>Pre-check pending snapshot inside a small {@code REQUIRED}
     *       read.</li>
     *   <li>Supabase object-info call OUTSIDE any DB transaction.</li>
     *   <li>Verify size + content type against pending expectations.</li>
     *   <li>If verify fails: short {@code REQUIRED} transaction marks
     *       pending {@code CANCELED} and enqueues {@code BIND_FAIL_NEW}.
     *       The method then throws so the controller surfaces the error.</li>
     *   <li>Call {@link TransactionalPaidDocumentBinder#bindPaidCreate},
     *       which opens its own {@code REQUIRED} transaction for the
     *       Document + DocumentFile + Pending flip.</li>
     *   <li>Return mapped {@link DocumentCardDto}.</li>
     * </ol>
     *
     * @param metadata      validated create payload (PAID shape only)
     * @param currentUser   authenticated owner (must match
     *                      {@code pending.user})
     * @return the mapped card DTO after the binder transaction commits
     */
    DocumentCardDto orchestratePaidCreate(
            DocumentCreateRequestDto metadata,
            User currentUser);
}
