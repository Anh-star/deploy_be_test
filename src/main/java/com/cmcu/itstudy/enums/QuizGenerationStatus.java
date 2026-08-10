package com.cmcu.itstudy.enums;

/**
 * Lifecycle of a Quiz AI generation request.
 *
 * <p>Phase 2B only persists and queries these statuses. Side effects
 * (n8n dispatch, retry, callback) belong to later phases and must NOT
 * be triggered from {@code @PrePersist} or constructors.
 *
 * <ul>
 *   <li>{@link #WAITING_SOURCE} — DOC / DOCX source is not yet
 *       AI-readable. The required artifact is an AI-readable FULL
 *       preview PDF; PDF uploads start in {@link #QUEUED} directly
 *       because the original PDF is itself the AI-readable source.</li>
 *   <li>{@link #QUEUED} — source is ready for future dispatch (PDF, or
 *       DOC/DOCX once the FULL preview PDF has been materialized).</li>
 *   <li>{@link #PROCESSING} — dispatched, awaiting callback.</li>
 *   <li>{@link #READY} — Quiz row materialized; downstream can render.</li>
 *   <li>{@link #FAILED} — terminal failure; manual / retry job may move.</li>
 *   <li>{@link #CANCELLED} — terminal; document deleted or user opted out.</li>
 * </ul>
 */
public enum QuizGenerationStatus {

    WAITING_SOURCE,
    QUEUED,
    PROCESSING,
    READY,
    FAILED,
    CANCELLED
}