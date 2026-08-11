package com.cmcu.itstudy.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Safe preview-state descriptor returned by
 * {@code GET /api/documents/{id}/preview} when async Office preview is
 * enabled and the FULL PDF artifact is not yet READY.
 *
 * <p>This DTO is intentionally a small whitelist of fields. It never
 * carries storage paths, signed URLs, service credentials, original
 * Office bytes, or stack traces.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPreviewStateResponseDto {

    /**
     * The preview mode, mirrored from {@link PreviewMode}.
     * Always {@code FULL} for the contributor-owner viewing path; the
     * state descriptor itself is the response shape.
     */
    private String mode;

    /**
     * The worker-managed FULL artifact status.
     * One of {@code PENDING}, {@code PROCESSING}, {@code RETRY}, or
     * {@code DEAD} for waiting / failed states.
     */
    private String status;

    /**
     * Bounded, user-safe Vietnamese message suitable for direct display.
     */
    private String message;

    /**
     * {@code true} when the worker may still produce the FULL PDF
     * (PENDING / PROCESSING / RETRY). {@code false} when the artifact
     * is DEAD and will not recover without operator intervention.
     */
    private boolean retryable;
}
