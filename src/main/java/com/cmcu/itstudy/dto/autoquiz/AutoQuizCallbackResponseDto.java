package com.cmcu.itstudy.dto.autoquiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Phase 2E callback response DTO returned to n8n.
 *
 * <p>The response carries enough information for n8n to confirm
 * success or detect an unexpected state, without exposing any
 * internal detail.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoQuizCallbackResponseDto {

    /** True when the callback was accepted for processing. */
    private boolean accepted;

    /**
     * The generation status after the callback was processed.
     * One of: READY, CANCELLED, PROCESSING.
     */
    private String status;

    /**
     * The generation ID this callback was processed for.
     * Present on every response including rejections.
     */
    private UUID generationId;

    /**
     * The newly created quiz ID, present only when a quiz was
     * successfully persisted (status == READY).
     */
    private UUID quizId;

    /**
     * A safe human-readable message. Never exposes the expected
     * dispatch token, storage paths, or internal identifiers.
     */
    private String message;
}
