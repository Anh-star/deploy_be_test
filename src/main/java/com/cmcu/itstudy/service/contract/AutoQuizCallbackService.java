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

    /**
     * Phase 7B.3 — terminal technical-failure callback.
     *
     * <p>Used by n8n to report that the workflow could NOT produce a
     * structurally valid quiz output (Structured Output Parser
     * failed, JSON schema invalid, upstream node crashed before any
     * AI output was emitted, etc.). This is a <strong>technical</strong>
     * failure path that is distinct from the business / semantic
     * {@link #processBusinessRejection} callback. A technical
     * failure MUST NOT be reported via {@code /reject}: that
     * endpoint hard-codes {@code FOCUS_TOPIC_MISMATCH} which would
     * lie about the document-content match.</p>
     *
     * <h3>Security model</h3>
     * <ul>
     *   <li>The supplied {@code dispatchToken} must match the row's
     *       stored token (constant-time comparison). Identical to
     *       the other two callbacks.</li>
     *   <li>The {@code errorCode} field is whitelisted server-side;
     *       only {@code AI_OUTPUT_INVALID}, {@code AI_SCHEMA_INVALID}
     *       and {@code AI_WORKFLOW_FAILED} are accepted. Any other
     *       value is rejected with HTTP 400.</li>
     *   <li>The supplied {@code message} is bounded to 200 chars at
     *       the DTO level AND sanitised through
     *       {@code SafeArtifactLastError} before storage, so a raw
     *       stack trace / model output / secrets never lands in the
     *       database. The message is logged but never echoed back
     *       to the client.</li>
     * </ul>
     *
     * <h3>Side effects (atomic via
     * {@code QuizGenerationRepository.markFailedFromProcessing})</h3>
     * <ul>
     *   <li>{@code status = FAILED} (terminal)</li>
     *   <li>{@code failedAt = now}</li>
     *   <li>{@code lastError} = the whitelisted error code
     *       (never the raw {@code message})</li>
     *   <li>{@code dispatchToken} and {@code nextAttemptAt} cleared</li>
     *   <li>NO {@code Quiz} row is created. NO questions, options
     *       or DocumentQuiz association.</li>
     *   <li>NO QuizGeneration history is deleted &mdash; the row
     *       becomes the immutable historical record of this
     *       technical attempt.</li>
     * </ul>
     *
     * <h3>Retry UI</h3>
     * <p>Because the row is terminal, the existing retry UI flow
     * (FE creates a NEW {@code QuizGeneration} via
     * {@code createMyDocumentAutoQuiz}) works without any change.
     * The Phase 7B.2 lineage mechanism keeps the old FAILED row
     * in history and the new generation as the current attempt.</p>
     *
     * @param generationId the generation ID from the URL path
     * @param dispatchToken the token from the
     *        {@code X-Auto-Quiz-Dispatch-Token} header
     * @param request the technical-failure body. {@code errorCode}
     *        is required and must be whitelisted;
     *        {@code message} is optional and bounded.
     * @return a response carrying {@code status = FAILED} and a
     *         safe {@code message} so the caller never sees raw
     *         error text
     * @throws com.cmcu.itstudy.handle.AutoQuizCallbackAccessDeniedException
     *         when the generation does not exist, the token does
     *         not match, the generation is not in PROCESSING state,
     *         the lease was invalidated by another writer, or the
     *         supplied {@code errorCode} is not whitelisted
     */
    AutoQuizCallbackResponseDto processTechnicalFailure(
            UUID generationId,
            UUID dispatchToken,
            com.cmcu.itstudy.dto.autoquiz
                    .AutoQuizTechnicalFailureRequestDto request);
}
