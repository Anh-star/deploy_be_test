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
}
