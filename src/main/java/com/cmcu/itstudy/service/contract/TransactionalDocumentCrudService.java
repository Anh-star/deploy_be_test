package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.User;

import java.util.Optional;

/**
 * Transactional core for the FREE {@code /api/my-documents} POST flow.
 *
 * <p>This service deliberately wraps the existing FREE document create
 * logic in its own {@code @Transactional(REQUIRED)} bean so the
 * command router can call it WITHOUT launching the PaidDocumentUploadOrchestrator
 * (which depends on a remote Supabase HTTP call that must NEVER run inside
 * a database transaction).
 *
 * <h2>Why this is its own bean</h2>
 * <ul>
 *   <li>Self-invocation: an internal {@code this.createDocument(...)}
 *       call inside {@code DocumentServiceImpl} bypasses the Spring AOP
 *       transaction proxy, so the existing public method stays
 *       {@code @Transactional} for backward compatibility.</li>
 *   <li>Isolation: the router needs a different bean boundary so it
 *       can compose free-create with paid-create orchestrator.</li>
 * </ul>
 *
 * <p>The free-create behavior is unchanged. No Supabase HTTP is ever
 * issued from this service. The class is added because the create flow
 * now has to compose two distinct transactional shapes (free {@code REQUIRED}
 * vs paid {@code REQUIRED} after a non-transactional verification step).
 *
 * <h2>Two distinct builders</h2>
 * <p>The service exposes TWO separate builders for the primary
 * {@link DocumentFile} so the paid and free paths cannot accidentally
 * share logic:
 * <ul>
 *   <li>{@link #buildPrimaryDocumentFile} — FREE-shape builder.
 *       Trusts the request's {@code documentUrl} + {@code storagePath}
 *       + {@code fileName} + {@code sizeBytes}; never touches
 *       {@code storageBucket} / {@code mimeType} (they are public-bucket
 *       rows and the public URL is the source of truth).</li>
 *   <li>{@link #buildPaidDocumentFile} — PAID-shape builder.
 *       Reuses the same field layout but explicitly sets
 *       {@code storageBucket} from the verified
 *       {@link com.cmcu.itstudy.entity.PendingStorageUpload},
 *       {@code mimeType} from the verified expected MIME,
 *       {@code fileUrl = null}, and {@code sizeBytes} from the verified
 *       {@link com.cmcu.itstudy.dto.storage.StorageObjectInfo}.</li>
 * </ul>
 *
 * <p>The split is intentional: a paid file MUST never inherit a
 * {@code fileUrl} from the request, and a free file MUST never be
 * tied to the private bucket. Sharing a single builder would risk
 * a regression where one path's invariants leak into the other.
 */
public interface TransactionalDocumentCrudService {

    /**
     * Create a FREE document (bucket = public). All existing field
     * semantics ({@code documentUrl}, {@code storagePath}, tags,
     * category, file type detection, primary {@link DocumentFile},
     * response mapping) are preserved.
     *
     * @param request validated create payload (FREE shape only)
     * @param currentUser authenticated owner
     * @return mapped card DTO
     */
    DocumentCardDto createFreeDocument(DocumentCreateRequestDto request, User currentUser);

    /**
     * Build a primary {@link DocumentFile} for a FREE document.
     *
     * <p>The result carries {@code fileUrl} from the request and
     * {@code storagePath} from the request. {@code storageBucket}
     * and {@code mimeType} are NOT set on this builder — the free path
     * serves public-bucket rows where {@code fileUrl} is the source
     * of truth.
     *
     * @param document parent document
     * @param storagePath server-resolved object path
     * @param fileUrl authoritative public URL
     * @param originalFileName validated filename
     * @param sizeBytes verified size in bytes
     */
    DocumentFile buildPrimaryDocumentFile(
            Document document,
            String storagePath,
            String fileUrl,
            String originalFileName,
            Long sizeBytes);

    /**
     * Build a primary {@link DocumentFile} for a PAID document.
     *
     * <p>The result is independent of the FREE builder. It explicitly
     * sets:
     * <ul>
     *   <li>{@code fileUrl = null} — paid files never expose a public
     *       URL on the row.</li>
     *   <li>{@code storageBucket} from the verified pending upload
     *       (must be non-blank).</li>
     *   <li>{@code mimeType} from the verified expected MIME
     *       (must be non-blank).</li>
     *   <li>{@code storagePath} from the verified pending upload
     *       (must be non-blank).</li>
     *   <li>{@code originalFileName} from the verified pending upload.</li>
     *   <li>{@code sizeBytes} from the verified
     *       {@link com.cmcu.itstudy.dto.storage.StorageObjectInfo}.</li>
     *   <li>{@code primary = true}.</li>
     * </ul>
     *
     * <p>The PAID builder MUST NOT inherit any field from the
     * request DTO — every authoritative value comes from the
     * server-resolved pending upload + verified object info. This is
     * the single source of truth for paid files.
     */
    DocumentFile buildPaidDocumentFile(
            Document document,
            String storageBucket,
            String storagePath,
            String mimeType,
            String originalFileName,
            Long sizeBytes);

    /**
     * Optional empty payload from a request — convenience for tests
     * and controllers that want to expose the empty request shape
     * uniformly. NOT a transactional operation.
     */
    default Optional<DocumentFile> noop() {
        return Optional.empty();
    }
}
