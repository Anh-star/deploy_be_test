package com.cmcu.itstudy.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight snapshot of the async Office-to-PDF preview artifact
 * status for a single document.
 *
 * <p>Returned by {@code GET /api/admin/documents/{id}/preview-status}
 * which is used by the frontend moderator review page to decide when to
 * enable the approve button for Office documents.
 *
 * <p>This DTO is read-only and never exposes Supabase storage paths,
 * signed URLs, or service credentials.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPreviewStatusDto {

    /**
     * Whether this document is an Office file (DOC/DOCX) that is managed
     * by the async preview worker.
     */
    private boolean officeDocument;

    /**
     * The worker-managed status of the FULL preview artifact.
     * One of:
     * <ul>
     *   <li>{@code PENDING} — created, not yet claimed by the worker</li>
     *   <li>{@code PROCESSING} — claimed and being converted</li>
     *   <li>{@code READY} — PDF successfully uploaded to storage</li>
     *   <li>{@code RETRY} — retryable failure; worker may reclaim later</li>
     *   <li>{@code DEAD} — terminal failure or max attempts reached</li>
     * </ul>
     * {@code null} when {@code officeDocument} is {@code false}.
     */
    private DocumentPreviewArtifactStatusDto fullStatus;

    /**
     * Human-readable Vietnamese error message from the last failed attempt.
     * {@code null} when the artifact is not in a failed state.
     * Bounded: never contains stack traces, internal paths, or credentials.
     */
    private String lastError;

    /**
     * Number of conversion attempts so far.
     * {@code null} when {@code officeDocument} is {@code false}.
     */
    private Integer attemptCount;

    /**
     * Maximum number of conversion attempts before the artifact becomes DEAD.
     * {@code null} when {@code officeDocument} is {@code false}.
     */
    private Integer maxAttempts;

    /**
     * Total page count of the FULL preview PDF, once known.
     * {@code null} when the artifact has not produced a PDF yet
     * (PENDING, PROCESSING) or when the artifact is not an Office file.
     */
    private Integer pageCount;
}
