package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaim;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service boundary for short-lived database claim operations against
 * {@code DocumentPreviewArtifact}.
 *
 * <p>Phase&nbsp;O2 implements the persistence-only side of the worker
 * claim lifecycle. The service:</p>
 * <ul>
 *   <li>does NOT perform any remote storage I/O (no Supabase calls,
 *       no LibreOffice invocation, no PDF generation);</li>
 *   <li>does NOT execute long-running work inside a database
 *       transaction;</li>
 *   <li>delegates all atomic transitions to the
 *       {@code DocumentPreviewArtifactClaimRepository} fragment;</li>
 *   <li>centralises the {@code Clock}-based current-time acquisition
 *       so the rest of the worker does not call
 *       {@code LocalDateTime.now()} in scattered locations.</li>
 * </ul>
 *
 * <p>Concrete orchestration (uploading the PDF, calling the converter,
 * notifying the moderator) lives in Phase&nbsp;O3 and is deliberately
 * absent here.</p>
 */
public interface DocumentPreviewArtifactClaimService {

    /**
     * Atomically claims up to {@code batchSize} ready preview artifacts
     * in oldest-first order and returns immutable snapshots. The
     * transaction commits immediately after the claim SQL; remote work
     * is performed outside this method by the Phase&nbsp;O3 worker.
     *
     * @param batchSize   1..configured safe maximum (currently
     *                    {@value com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaimRepository#MAX_BATCH_SIZE})
     * @param staleBefore cutoff for reclaiming abandoned
     *                    {@code PROCESSING} rows; must be &le;
     *                    {@code now}
     * @return claimed snapshots, possibly empty, never null
     */
    List<DocumentPreviewArtifactClaim> claimBatch(int batchSize,
            LocalDateTime staleBefore);

    /**
     * Returns the application clock used to derive {@code now} for
     * {@link #claimBatch(int, LocalDateTime)}. Exposed so the worker
     * (Phase&nbsp;O3) can read a consistent {@code now} value without
     * calling {@link LocalDateTime#now()} directly.
     */
    java.time.Clock clock();
}
