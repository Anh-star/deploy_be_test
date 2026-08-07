package com.cmcu.itstudy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

/**
 * Typed configuration for the asynchronous DOC / DOCX preview worker
 * introduced in Phase&nbsp;O3.
 *
 * <p>The worker is a fixed-delay batch scheduler that claims preview
 * artifacts through the existing Phase&nbsp;O2 atomic claim SQL,
 * downloads the original Office document through the existing
 * {@code SupabaseStorageService}, runs the Phase&nbsp;O1
 * {@code LibreOfficeDocumentConverter} over the downloaded bytes,
 * validates the resulting PDF through the frozen
 * {@code OfficePdfValidationService}, and uploads the generated PDF
 * preview back into Supabase. The worker also renders a LIMITED
 * derivative when the corresponding FULL artifact is already READY.
 *
 * <p>Every field carries a safe default so the worker can be wired
 * without touching {@code application.properties}. The defaults
 * pin the worker to a disabled, single-batch, modest-volume profile so
 * that an accidental startup can never trigger a real upload cycle.
 * Operators are expected to enable the worker explicitly per
 * environment by overriding the corresponding environment variables.
 *
 * <h2>Environment variable names</h2>
 * <p>Spring Boot relaxed binding maps each property to the following
 * uppercase env names. Operators can override defaults per environment
 * without touching {@code application.properties}:</p>
 * <ul>
 *   <li>{@code APP_DOCUMENT_PREVIEW_WORKER_ENABLED}</li>
 *   <li>{@code APP_DOCUMENT_PREVIEW_WORKER_BATCH_SIZE}</li>
 *   <li>{@code APP_DOCUMENT_PREVIEW_WORKER_FIXED_DELAY_MS}</li>
 *   <li>{@code APP_DOCUMENT_PREVIEW_WORKER_STALE_AFTER}</li>
 *   <li>{@code APP_DOCUMENT_PREVIEW_WORKER_RETRY_BASE_DELAY}</li>
 *   <li>{@code APP_DOCUMENT_PREVIEW_WORKER_RETRY_MAX_DELAY}</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.document-preview.worker")
public class DocumentPreviewWorkerProperties {

    /**
     * Master switch. When {@code false} the worker cycle is a no-op:
     * no claim, no download, no conversion, no upload, no Supabase
     * call. The default value is {@code false} so an accidental
     * deployment cannot trigger a real storage cycle.
     */
    private boolean enabled = false;

    /**
     * Maximum number of artifacts claimed per worker cycle.
     * Bounded by {@code DocumentPreviewArtifactClaimRepository.MAX_BATCH_SIZE}
     * (50) at runtime.
     */
    private int batchSize = 5;

    /**
     * Fixed delay between two consecutive worker cycles. The worker
     * starts the next cycle only after the previous cycle has fully
     * <em>dispatched</em> its batch — processing itself is delegated
     * to a bounded executor and runs in parallel with the next
     * scheduling cycle. The 3-second default sits inside the
     * 2–5&nbsp;second latency target without becoming a busy loop.
     *
     * <p>Operators can override per deployment via the environment
     * variable {@code APP_DOCUMENT_PREVIEW_WORKER_FIXED_DELAY_MS}.
     * The value is clamped to the safe range
     * {@code [250, 30_000]} by {@link #validate()}.</p>
     */
    private long fixedDelayMs = 3_000L;

    /**
     * Hard cap on the number of processing threads that may run in
     * parallel inside this JVM. The scheduler only <em>dispatches</em>
     * claimed artifacts to this executor; it never blocks on the
     * processor. The default 2 matches the
     * {@code app.preview.office.max-concurrent-conversions} default so
     * LibreOffice never has more in-flight conversions than
     * permits available.
     */
    private int processingThreads = 2;

    /**
     * Capacity of the bounded executor queue that buffers artifacts
     * waiting for a free processing thread. When the queue is full the
     * executor rejects the task and the worker leaves the artifact in
     * {@code PROCESSING}; the stale-PROCESSING reclaim path then
     * re-claims it on the next cycle.
     */
    private int processingQueueCapacity = 10;

    /**
     * Stale-after cutoff for reclaiming abandoned {@code PROCESSING}
     * rows. Rows whose {@code claimed_at} is older than
     * {@code now - staleAfter} are eligible for reclaim.
     */
    private Duration staleAfter = Duration.ofMinutes(15);

    /**
     * Base retry delay. The bounded exponential backoff uses
     * {@code retryBaseDelay * 2^(attemptCount - 1)} capped by
     * {@link #retryMaxDelay}.
     */
    private Duration retryBaseDelay = Duration.ofMinutes(1);

    /**
     * Maximum retry delay. The exponential backoff is hard-capped at
     * this value so the schedule never explodes.
     */
    private Duration retryMaxDelay = Duration.ofMinutes(30);

    /**
     * Validate the bound property set. Invoked once at Spring bean
     * creation time by the configuration class so that an invalid
     * configuration fails fast.
     *
     * @throws IllegalStateException when any field violates the O3
     *         configuration contract
     */
    public void validate() {
        StringBuilder errors = new StringBuilder();

        if (batchSize <= 0) {
            errors.append("batchSize must be > 0; ");
        }
        if (batchSize > 50) {
            errors.append("batchSize must be <= 50 (claim repository hard cap); ");
        }
        if (fixedDelayMs < 250L || fixedDelayMs > 30_000L) {
            errors.append("fixedDelayMs must be in [250, 30000]; ");
        }
        if (processingThreads < 1 || processingThreads > 16) {
            errors.append("processingThreads must be in [1, 16]; ");
        }
        if (processingQueueCapacity < processingThreads
                || processingQueueCapacity > 200) {
            errors.append(
                    "processingQueueCapacity must be in ["
                            + processingThreads + ", 200]; ");
        }
        requirePositive(staleAfter, "staleAfter", errors);
        requirePositive(retryBaseDelay, "retryBaseDelay", errors);
        requirePositive(retryMaxDelay, "retryMaxDelay", errors);
        if (retryMaxDelay != null && retryBaseDelay != null
                && retryMaxDelay.compareTo(retryBaseDelay) < 0) {
            errors.append("retryMaxDelay must be >= retryBaseDelay; ");
        }

        if (errors.length() > 0) {
            throw new IllegalStateException(
                    "Invalid app.document-preview.worker configuration: "
                            + errors);
        }
    }

    private static void requirePositive(Duration d, String name,
                                        StringBuilder errors) {
        if (d == null || d.isNegative() || d.isZero()) {
            errors.append(name).append(" must be positive; ");
        }
    }
}
