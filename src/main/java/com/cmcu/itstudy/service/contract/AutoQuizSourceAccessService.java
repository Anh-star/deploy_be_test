package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.autoquiz.AutoQuizSourceResolutionDto;
import com.cmcu.itstudy.handle.AutoQuizSourceAccessDeniedException;

import java.util.UUID;

/**
 * Phase 2E-A: secure source-access service for the Auto Quiz pipeline.
 *
 * <p>The n8n dispatch worker calls resolveSource(UUID, UUID) AFTER
 * receiving the dispatch payload from the backend. The service
 * performs the full server-side access decision and returns the
 * resolved AutoQuizSourceResolutionDto that the controller will use
 * to download the PDF bytes via
 * SupabaseStorageService.downloadPrivateObject(String, String). The
 * controller is then responsible for streaming the bytes back with
 * Content-Type: application/pdf.</p>
 *
 * <h2>Access gates</h2>
 * <ol>
 *   <li>generationId must resolve to a QuizGeneration row.</li>
 *   <li>generation.status MUST equal PROCESSING. Any other status
 *       (QUEUED, WAITING_SOURCE, READY, FAILED, CANCELLED) is
 *       rejected.</li>
 *   <li>suppliedDispatchToken MUST equal generation.dispatchToken
 *       (constant-time comparison). A blank / null token is
 *       rejected.</li>
 *   <li>The generation's primary DocumentFile must exist and must
 *       have a non-blank bucket / path.</li>
 *   <li>For PDF originals, bytes come from
 *       DocumentFile.storageBucket / DocumentFile.storagePath.</li>
 *   <li>For DOC/DOCX, bytes come from the most recent READY FULL
 *       DocumentPreviewArtifact. If no READY FULL artifact exists
 *       yet, the request is rejected
 *       (AutoQuizSourceAccessDeniedException.Reason.PREVIEW_NOT_READY).
 *       LibreOffice / POI are NEVER invoked at request time.</li>
 * </ol>
 *
 * <h2>Read-only contract</h2>
 * <p>This service is strictly READ-ONLY with respect to the
 * generation lifecycle. It MUST NOT:</p>
 * <ul>
 *   <li>increment attempts;</li>
 *   <li>rotate or clear dispatchToken;</li>
 *   <li>transition PROCESSING to READY or PROCESSING to FAILED;</li>
 *   <li>modify nextAttemptAt, lastAttemptAt, or lastError;</li>
 *   <li>issue any HTTP request to the n8n webhook.</li>
 * </ul>
 *
 * <h2>Paid-document protection</h2>
 * <p>Paid documents continue to be served through the private Supabase
 * bucket (never a public URL, never a signed URL). The endpoint does
 * not surface the bucket or path to the caller.</p>
 *
 * @see AutoQuizSourceAccessDeniedException
 */
public interface AutoQuizSourceAccessService {

    /**
     * Resolve the storage descriptor for a QuizGeneration so the
     * caller can stream the PDF bytes back to the dispatch worker.
     *
     * @param generationId         the generation row id (UUID from path)
     * @param suppliedDispatchToken the dispatch token supplied via the
     *                              X-Auto-Quiz-Dispatch-Token header;
     *                              MUST match the row's
     *                              dispatchToken
     * @return the server-resolved storage descriptor
     * @throws AutoQuizSourceAccessDeniedException when any of the
     *         documented access gates fail
     */
    AutoQuizSourceResolutionDto resolveSource(
            UUID generationId, UUID suppliedDispatchToken);
}
