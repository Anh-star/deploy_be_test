package com.cmcu.itstudy.handle;

/**
 * Phase 2E exception thrown when the auto-quiz success callback
 * request is rejected by the service layer.
 *
 * <p>This exception is categorically different from
 * {@link AutoQuizSourceAccessDeniedException} (which governs the
 * secure source endpoint). The callback rejection reasons
 * are distinct and are never surfaced to the API caller
 * outside the envelope defined by
 * {@link GlobalExceptionHandler}.
 */
public class AutoQuizCallbackAccessDeniedException extends RuntimeException {

    public enum Reason {
        MISSING_TOKEN,
        GENERATION_NOT_FOUND,
        TOKEN_MISMATCH,
        CANCELLED,
        ALREADY_READY,
        QUESTION_COUNT_MISMATCH,
        QUESTIONS_EMPTY,
        ANSWER_COUNT_WRONG,
        CORRECT_INDEX_OUT_OF_RANGE,
        UNKNOWN_ERROR_CODE
    }

    private final Reason reason;

    public AutoQuizCallbackAccessDeniedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
