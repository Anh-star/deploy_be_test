package com.cmcu.itstudy.handle;

/**
 * Phase 2E-A: secure-source access for n8n denied.
 *
 * <p>Raised by AutoQuizSourceAccessService.deliverSourceForDispatchToken
 * when a request from the dispatch worker fails one of the documented
 * access gates:</p>
 *
 * <ul>
 *   <li>the X-Auto-Quiz-Dispatch-Token header is missing or blank;</li>
 *   <li>the generation row cannot be found by id;</li>
 *   <li>the generation is in a status that is NOT PROCESSING
 *       (e.g. QUEUED, WAITING_SOURCE, READY, FAILED, CANCELLED);</li>
 *   <li>the supplied dispatch token does not match
 *       QuizGeneration.dispatchToken;</li>
 *   <li>the generation has no associated primary DocumentFile row,
 *       or the storage bucket/path is missing;</li>
 *   <li>the generation links to a document whose primary file is a
 *       DOC/DOCX but no READY FULL preview artifact exists yet.</li>
 * </ul>
 *
 * <p>The exception message is intentionally safe: it does not leak
 * the expected token, the bucket, the storage path, or any internal
 * identifier. It is translated by GlobalExceptionHandler into a 403
 * Forbidden response carrying only a generic human-readable
 * message.</p>
 *
 * <p>This is a READ-ONLY failure: the generation lifecycle is never
 * mutated. No attempts increment, no dispatchToken rotation, no
 * PROCESSING to FAILED transition.</p>
 */
public class AutoQuizSourceAccessDeniedException extends RuntimeException {

    /**
     * Categorical reason. Used internally for logging only; the
     * public response always carries a single safe message.
     */
    public enum Reason {
        MISSING_TOKEN,
        GENERATION_NOT_FOUND,
        STATUS_NOT_PROCESSING,
        TOKEN_MISMATCH,
        PRIMARY_FILE_MISSING,
        STORAGE_NOT_CONFIGURED,
        PREVIEW_NOT_READY,
        UNSUPPORTED_FILE_TYPE
    }

    private final Reason reason;

    public AutoQuizSourceAccessDeniedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
