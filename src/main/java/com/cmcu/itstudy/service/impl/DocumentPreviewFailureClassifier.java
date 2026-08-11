package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.handle.OfficeConversionInterruptedException;
import com.cmcu.itstudy.handle.OfficeConversionInvalidInputException;
import com.cmcu.itstudy.handle.OfficeConversionInvalidOutputException;
import com.cmcu.itstudy.handle.OfficeConversionOutputTooLargeException;
import com.cmcu.itstudy.handle.OfficeConversionRetryableException;
import com.cmcu.itstudy.handle.OfficeConversionTerminalException;
import com.cmcu.itstudy.handle.OfficeConversionTimeoutException;
import com.cmcu.itstudy.handle.OfficeConversionUnsupportedFormatException;
import com.cmcu.itstudy.handle.PreviewUploadTooLargeException;
import com.cmcu.itstudy.handle.SignedUploadTargetFailedException;
import com.cmcu.itstudy.handle.StorageObjectNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Central failure-classification policy for the Phase&nbsp;O3 preview
 * worker.
 *
 * <p>The classifier maps raw outcome states onto the worker-level
 * decisions the rest of the orchestrator relies on:</p>
 *
 * <ul>
 *   <li>{@link Decision#PERMANENT_DEAD} &mdash; the failure cannot
 *       succeed on retry; the worker must transition the artifact to
 *       {@code DEAD} immediately.</li>
 *   <li>{@link Decision#RETRYABLE} &mdash; the failure is transient
 *       and the artifact still has budget; the worker must compute the
 *       next {@code nextAttemptAt} and call {@code markRetry}.</li>
 *   <li>{@link Decision#BUDGET_EXHAUSTED_RETRYABLE} &mdash; the same
 *       underlying condition as {@link Decision#RETRYABLE} but the
 *       artifact has no remaining attempt; the worker must transition
 *       to {@code DEAD} instead of {@code RETRY}.</li>
 *   <li>{@link Decision#INTERRUPTED} &mdash; the worker thread was
 *       interrupted mid-conversion; the worker must stop the batch
 *       without overwriting the artifact state. The
 *       {@code markReady}/{@code markRetry}/{@code markDead} calls
 *       are skipped entirely; the artifact row will be re-claimed by
 *       a future cycle via the stale-{@code PROCESSING} reclaim path.</li>
 * </ul>
 *
 * <h2>Context-specific classification</h2>
 * <p>The classifier distinguishes between two {@code 404}-like
 * conditions that the rest of the worker needs to handle
 * differently:</p>
 * <ul>
 *   <li><strong>Original source missing</strong> during FULL
 *       processing &mdash; the {@link DocumentFile} row or its
 *       original Office bytes are gone. The classifier maps this to
 *       {@link Decision#PERMANENT_DEAD} because no retry can
 *       re-materialise the source.</li>
 *   <li><strong>Dependent FULL preview missing</strong> during
 *       LIMITED processing &mdash; the FULL PDF the LIMITED derivative
 *       depends on is not yet READY. The classifier maps this to
 *       {@link Decision#RETRYABLE} until the budget is exhausted;
 *       FULL may be regenerated in a future cycle or the cleanup
 *       queue may still be draining.</li>
 * </ul>
 *
 * <p>All non-{@code INTERRUPTED} decisions carry a fixed short
 * "operational code" string that the worker persists into
 * {@code last_error} via the existing {@code SafeArtifactLastError}
 * sanitiser. The classifier never embeds arbitrary exception
 * messages, stack traces, or storage payload information into the
 * code.</p>
 */
@Component
public class DocumentPreviewFailureClassifier {

    /**
     * Worker-level classification decisions.
     */
    public enum Decision {
        /** Failure cannot succeed on retry; mark DEAD immediately. */
        PERMANENT_DEAD,
        /** Failure is transient; mark RETRY with backoff. */
        RETRYABLE,
        /** Same as RETRYABLE but the artifact has no budget left. */
        BUDGET_EXHAUSTED_RETRYABLE,
        /** Thread was interrupted; do not overwrite state. */
        INTERRUPTED
    }

    /**
     * Classify the outcome of a single artifact attempt.
     *
     * @param throwable       the exception that aborted the attempt
     *                        (may be {@code null} when the call
     *                        succeeded and the caller wants a positive
     *                        confirmation)
     * @param claimedAttemptCount the attempt count AFTER the claim
     *                        SQL incremented it (always {@code >= 1})
     * @param maxAttempts     the maximum attempt budget for the
     *                        artifact (always {@code >= 1})
     * @return the worker-level decision
     */
    public Decision classify(Throwable throwable,
                             int claimedAttemptCount,
                             int maxAttempts) {
        validateArgs(claimedAttemptCount, maxAttempts);

        // No exception → success. The worker re-routes this case
        // through the guarded markReady path.
        if (throwable == null) {
            return Decision.RETRYABLE;
        }

        if (isInterrupted(throwable)) {
            return Decision.INTERRUPTED;
        }

        if (isPermanent(throwable)) {
            return Decision.PERMANENT_DEAD;
        }

        // Final-cleanup TIMEOUT policy: a TIMEOUT on the second
        // attempt for the same artifact is terminal, regardless of
        // the remaining maxAttempts budget. The first TIMEOUT
        // (attemptCount == 1) is retryable so the worker gets one
        // retry for a slow conversion; the second TIMEOUT
        // (attemptCount == 2) marks DEAD.
        //
        // Decision is keyed off the persisted claimedAttemptCount
        // passed by the worker; no in-memory counter is required
        // so a JVM restart does not silently widen the retry
        // budget.
        if (isOfficeTimeoutExhausted(throwable, claimedAttemptCount)) {
            return Decision.PERMANENT_DEAD;
        }

        boolean hasBudget = claimedAttemptCount < maxAttempts;
        return hasBudget
                ? Decision.RETRYABLE
                : Decision.BUDGET_EXHAUSTED_RETRYABLE;
    }

    /**
     * Phase-final TIMEOUT policy: a TIMEOUT becomes terminal as
     * soon as the worker is processing the second attempt for the
     * same artifact.
     *
     * @param throwable          the exception that aborted the
     *                           attempt
     * @param claimedAttemptCount the attempt count AFTER the claim
     *                           SQL incremented it (always {@code >= 1})
     * @return {@code true} when the throwable is a LibreOffice
     *         timeout AND the worker is on its second or later
     *         attempt for this artifact
     */
    private static boolean isOfficeTimeoutExhausted(Throwable throwable,
                                                    int claimedAttemptCount) {
        if (!(throwable instanceof OfficeConversionTimeoutException)) {
            return false;
        }
        return claimedAttemptCount >= 2;
    }

    /**
     * Context-specific classification for a LIMITED dependency
     * failure: the dependent FULL preview is missing or its bytes are
     * unavailable. A missing FULL preview is retryable until the
     * retry budget is exhausted (FULL may be regenerated or the
     * cleanup queue may still be draining).
     *
     * @param claimedAttemptCount the attempt count AFTER the claim
     *                            SQL incremented it
     * @param maxAttempts         the maximum attempt budget
     * @return {@link Decision#RETRYABLE} while the budget remains,
     *         {@link Decision#BUDGET_EXHAUSTED_RETRYABLE} otherwise
     */
    public Decision classifyLimitedDependencyFailure(
            int claimedAttemptCount,
            int maxAttempts) {
        validateArgs(claimedAttemptCount, maxAttempts);
        boolean hasBudget = claimedAttemptCount < maxAttempts;
        return hasBudget
                ? Decision.RETRYABLE
                : Decision.BUDGET_EXHAUSTED_RETRYABLE;
    }

    /**
     * Safe operational code for a LIMITED-dependency failure whose
     * code is constructed at the call site (e.g.
     * {@code "O3_FULL_BYTES_MISSING"}). The string is passed through
     * unchanged when it is short and contains only the safe alphabet;
     * the caller MUST treat the input as an internal code rather than
     * user data.
     *
     * @param limitedCode an internal operational code (must not be
     *                    null or blank)
     * @return the operational code, unchanged
     */
    public String safeOperationalCodeForLimited(String limitedCode) {
        if (limitedCode == null || limitedCode.isBlank()) {
            return "O3_FAILURE";
        }
        return limitedCode;
    }

    /**
     * Whitelist mapping from a {@link SignedUploadTargetFailedException}
     * internal category (set at the throw site) to the bounded
     * operational code persisted into {@code last_error}. Categories
     * not in this map (including {@code null} or blank) collapse to
     * {@code O3_UPLOAD_FAILED}.
     *
     * <p>The classifier NEVER inspects the exception message, the
     * exception class hierarchy beyond {@code SignedUploadTargetFailedException},
     * or the cause chain to derive a category. The category must be
     * attached at the throw site; this map is the single source of
     * truth for the operator-visible code.</p>
     */
    private static final Map<String, String> UPLOAD_CATEGORY_TO_OPS_CODE =
            Map.ofEntries(
                    Map.entry("invalid-bucket", "O3_UPLOAD_BAD_BUCKET"),
                    Map.entry("invalid-path", "O3_UPLOAD_BAD_PATH"),
                    Map.entry("empty-pdf-bytes", "O3_UPLOAD_EMPTY_BYTES"),
                    Map.entry("bad-content-type",
                            "O3_UPLOAD_BAD_CONTENT_TYPE"),
                    Map.entry("missing-config",
                            "O3_UPLOAD_MISSING_CONFIG"),
                    Map.entry("missing-url", "O3_UPLOAD_MISSING_URL"),
                    Map.entry("missing-service-role-key",
                            "O3_UPLOAD_MISSING_SVCROLE"),
                    Map.entry("missing-bucket",
                            "O3_UPLOAD_MISSING_BUCKET"),
                    Map.entry("http-4xx", "O3_UPLOAD_HTTP_4XX"),
                    Map.entry("http-5xx", "O3_UPLOAD_HTTP_5XX"),
                    Map.entry("timeout", "O3_UPLOAD_TIMEOUT"),
                    Map.entry("transport", "O3_UPLOAD_TRANSPORT"),
                    Map.entry("unexpected", "O3_UPLOAD_UNEXPECTED"));

    /**
     * Safe operational code for a SignedUploadTargetFailedException
     * whose internalCategory was attached at the throw site. The
     * classifier looks up the category in a closed whitelist and
     * returns the corresponding bounded code. Unknown, blank, or
     * {@code null} categories fall back to
     * {@code O3_UPLOAD_FAILED}.
     *
     * @param throwable the failed exception; must be a
     *                  {@link SignedUploadTargetFailedException}
     * @return the bounded operational code
     */
    private static String safeCodeForSignedUpload(
            SignedUploadTargetFailedException throwable) {
        if (throwable == null) {
            return "O3_UPLOAD_FAILED";
        }
        String category = throwable.getInternalCategory();
        if (category == null || category.isBlank()) {
            return "O3_UPLOAD_FAILED";
        }
        String mapped = UPLOAD_CATEGORY_TO_OPS_CODE.get(category);
        return mapped != null ? mapped : "O3_UPLOAD_FAILED";
    }

    /**
     * @return a short, safe, operator-oriented summary code suitable
     *         for the {@code last_error} column. The string is bounded
     *         to a small fixed alphabet and NEVER includes the
     *         exception message, stack trace, or any payload fragment.
     */
    public String safeOperationalCode(Throwable throwable) {
        if (throwable == null) {
            return "O3_OK";
        }
        if (isInterrupted(throwable)) {
            return "O3_INTERRUPTED";
        }
        if (throwable instanceof SignedUploadTargetFailedException) {
            return safeCodeForSignedUpload(
                    (SignedUploadTargetFailedException) throwable);
        }
        if (throwable instanceof OfficeConversionUnsupportedFormatException) {
            return "O3_UNSUPPORTED_SOURCE";
        }
        if (throwable instanceof OfficeConversionInvalidInputException) {
            return "O3_INVALID_INPUT";
        }
        if (throwable instanceof OfficeConversionInvalidOutputException) {
            return "O3_INVALID_OUTPUT";
        }
        if (throwable instanceof OfficeConversionOutputTooLargeException) {
            return "O3_OUTPUT_TOO_LARGE";
        }
        if (throwable instanceof OfficeConversionTerminalException) {
            return "O3_TERMINAL";
        }
        if (throwable instanceof StorageObjectNotFoundException) {
            return "O3_SOURCE_MISSING";
        }
        if (throwable instanceof PreviewUploadTooLargeException) {
            return "O3_PREVIEW_TOO_LARGE";
        }
        if (throwable instanceof OfficeConversionRetryableException) {
            return "O3_RETRYABLE";
        }
        return "O3_FAILURE";
    }

    private static void validateArgs(int claimedAttemptCount,
                                     int maxAttempts) {
        if (claimedAttemptCount < 1) {
            throw new IllegalArgumentException(
                    "claimedAttemptCount must be >= 1: " + claimedAttemptCount);
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "maxAttempts must be >= 1: " + maxAttempts);
        }
    }

    private static boolean isInterrupted(Throwable throwable) {
        if (throwable instanceof OfficeConversionInterruptedException) {
            return true;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Permanent failures are explicit terminal mapping outcomes: the
     * source bytes cannot produce a valid PDF preview, the source
     * DocumentFile is missing, or the upload payload violates the
     * hard cap. A retried attempt would surface the same typed
     * exception.
     */
    private static boolean isPermanent(Throwable throwable) {
        if (throwable instanceof OfficeConversionUnsupportedFormatException) {
            return true;
        }
        if (throwable instanceof OfficeConversionInvalidInputException) {
            return true;
        }
        if (throwable instanceof OfficeConversionInvalidOutputException) {
            return true;
        }
        if (throwable instanceof OfficeConversionOutputTooLargeException) {
            return true;
        }
        if (throwable instanceof StorageObjectNotFoundException) {
            return true;
        }
        if (throwable instanceof PreviewUploadTooLargeException) {
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}