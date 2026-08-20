package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.autoquiz.AutoQuizCallbackRequestDto;
import com.cmcu.itstudy.dto.autoquiz.AutoQuizCallbackResponseDto;
import com.cmcu.itstudy.handle.AutoQuizCallbackAccessDeniedException;

import java.util.UUID;

/**
 * Phase 2E contract for the n8n-to-backend success callback.
 *
 * <p>Security model: the caller (n8n) authenticates by sending the
 * exact {@code dispatchToken} that the dispatcher issued when the
 * generation was claimed. The token is validated by constant-time
 * comparison against the stored value. No JWT or user credential
 * is required for this machine-to-machine endpoint.
 *
 * <p>Idempotency: calling {@code processCallback} more than once for
 * the same {@code generationId} + valid {@code dispatchToken} is
 * safe. If the generation is already {@code READY}, the already-
 * created {@code quizId} is returned without creating duplicates.
 *
 * <p>CANCELLED wins: if the generation has been cancelled since the
 * n8n dispatch, no quiz is created and the callback is rejected.
 */
public interface AutoQuizCallbackService {

    /**
     * Process the success callback from n8n.
     *
     * @param generationId the generation ID from the URL path
     * @param dispatchToken the token from the
     *        {@code X-Auto-Quiz-Dispatch-Token} header
     * @param request the callback payload from n8n
     * @return a response carrying the status, generation ID, and
     *         optionally the new quiz ID
     * @throws AutoQuizCallbackAccessDeniedException when the
     *         generation does not exist, the token does not match,
     *         the generation is CANCELLED, or the payload is invalid
     */
    AutoQuizCallbackResponseDto processCallback(
            UUID generationId,
            UUID dispatchToken,
            AutoQuizCallbackRequestDto request);

    /**
     * Phase 5A — terminal business-rejection callback.
     *
     * <p>Used by n8n to report that a generation must NOT be turned
     * into a {@code Quiz} row because a semantic / business condition
     * failed (initially: focus-topic mismatch with the document). The
     * backend hard-codes the rejection code; clients MUST NOT be able
     * to supply an arbitrary {@code lastError} string.</p>
     *
     * <p>Security model: identical to {@link #processCallback} —
     * the supplied {@code dispatchToken} must match the row's stored
     * token (constant-time comparison). The generation MUST be in
     * {@code PROCESSING}; any other status is rejected as
     * "not in PROCESSING state" via
     * {@link AutoQuizCallbackAccessDeniedException}.</p>
     *
     * <p>Side effects (atomic via
     * {@code QuizGenerationRepository.markFailedFromProcessing}):</p>
     * <ul>
     *   <li>{@code status = FAILED} (terminal)</li>
     *   <li>{@code failedAt = now}</li>
     *   <li>{@code lastError = "FOCUS_TOPIC_MISMATCH"}</li>
     *   <li>{@code dispatchToken} and {@code nextAttemptAt} cleared</li>
     *   <li>NO {@code Quiz} row is created — no questions, no
     *       options, no DocumentQuiz association.</li>
     * </ul>
     *
     * @param generationId the generation ID from the URL path
     * @param dispatchToken the token from the
     *        {@code X-Auto-Quiz-Dispatch-Token} header
     * @return a response carrying {@code status = FAILED} and a safe
     *         {@code message = "Generation rejected"} so the caller
     *         never sees raw Gemini text
     * @throws AutoQuizCallbackAccessDeniedException when the
     *         generation does not exist, the token does not match,
     *         the generation is not in PROCESSING state, or the
     *         lease was invalidated by another writer
     */
    AutoQuizCallbackResponseDto processBusinessRejection(
            UUID generationId,
            UUID dispatchToken);
}
