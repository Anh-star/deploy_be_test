package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.DocumentPreviewArtifact;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus;
import com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Bootstrap factory for the FRESH-Office preview-artifact lifecycle.
 *
 * <p>Called from the document create paths (free and paid) immediately
 * after the primary {@link DocumentFile} has been persisted. The factory
 * joins the caller's REQUIRED transaction via {@link Propagation#MANDATORY}
 * so the artifact row commits or rolls back atomically with the
 * enclosing Document and DocumentFile rows.</p>
 *
 * <p>Phase O4C (Office artifact creation bug fix) tightens the contract
 * to distinguish free and paid sources:</p>
 *
 * <ul>
 *   <li>Non-DOC/DOCX file type → no-op (PDF is handled by the legacy
 *       {@code PaidDocumentPreviewService} pipeline).</li>
 *   <li>Free DOC / DOCX (i.e. {@code paid == false}) → exactly one
 *       FULL PENDING artifact.</li>
 *   <li>Paid DOC / DOCX (i.e. {@code paid == true}) → exactly two
 *       artifacts: one FULL and one LIMITED, both PENDING, both
 *       with the same {@code documentFileId},
 *       {@code sourceChecksumSha256}, and
 *       {@code variantVersion = INITIAL_VARIANT_VERSION}.</li>
 * </ul>
 *
 * <h2>Time basis (consistent-now contract)</h2>
 * <p>The factory derives {@code createdAt}, {@code updatedAt}, and
 * {@code nextAttemptAt} from the application {@link Clock} injected at
 * construction time so the timestamps agree with the worker scheduler,
 * the claim predicate, and the backoff calculator on the same JVM.
 * The application clock is wired to {@link Clock#systemUTC()} in
 * {@code ApplicationClockConfig}, so the factory stamps a stable,
 * region-independent UTC value regardless of the host time-zone.
 * Setting {@code nextAttemptAt = createdAt} (i.e. NOW) means a fresh
 * artifact satisfies the production claim predicate
 * {@code next_attempt_at <= :now} on the very next worker cycle, so the
 * worker can pick it up without any scheduled delay.</p>
 *
 * <p>The {@link DocumentPreviewArtifact#prePersist()} callback also
 * uses {@link Clock#systemUTC()} as its fallback for entities that
 * bypass this factory, but the factory MUST set the timestamps
 * explicitly so the value can be tested against a fixed
 * {@link Clock}.</p>
 *
 * <h2>Idempotency contract</h2>
 * <p>If a matching FULL artifact (or FULL+LIMITED pair for paid docs)
 * already exists for the same {@code (documentFileId, artifactKind,
 * sourceChecksumSha256, variantVersion)} tuple, the factory MUST NOT
 * create a duplicate. The lookup uses the same business-key repository
 * methods as the worker so a re-issued create call, a transactional
 * retry, or a duplicate approval re-entry yields exactly the original
 * artifact set.</p>
 *
 * <h3>Concurrent safety</h3>
 * <p>Sequential idempotency is enforced by
 * {@link #existsArtifact(UUID, DocumentPreviewArtifactKind, String, int)},
 * which probes the same business-key tuple used by the controller and
 * the worker so a duplicate approval re-entry does not create
 * additional rows.</p>
 *
 * <p>Concurrent idempotency is a separate concern. The current schema
 * does NOT carry a unique constraint on
 * {@code (document_file_id, artifact_kind, source_checksum_sha256,
 * variant_version)} because this project does not ship a SQL
 * migration framework (no Flyway, no Liquibase, no
 * {@code db/migration/} folder). The {@code existsArtifact} probe is
 * therefore safe against sequential retries but cannot prevent two
 * parallel transactions from both successfully inserting a fresh row,
 * each of which only sees a {@code null} result when it probes.</p>
 *
 * <p>The factory catches {@link
 * org.springframework.dao.DataIntegrityViolationException} defensively
 * so a deployment that later opts to install a unique index on the
 * business-key tuple upgrades concurrency safety without a code
 * change. Until that unique index is added by a future migration, the
 * factory MUST be considered <strong>sequentially idempotent only
 * &mdash; NOT concurrency-safe</strong>.</p>
 *
 * <p>The factory MUST NOT delete, mutate, or change the status of
 * existing artifacts. It only adds NEW rows.</p>
 */
@Service
public class DocumentPreviewArtifactFactory {

    /**
     * Single source of truth for the initial variant version of a
     * freshly-created preview artifact. The literal {@code 1} MUST
     * NOT be repeated anywhere else in the project for this purpose.
     */
    public static final int INITIAL_VARIANT_VERSION = 1;

    private final DocumentPreviewArtifactRepository artifactRepository;
    private final Clock clock;

    public DocumentPreviewArtifactFactory(
            DocumentPreviewArtifactRepository artifactRepository,
            Clock clock) {
        this.artifactRepository = Objects.requireNonNull(artifactRepository,
                "artifactRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Persists one or two {@link DocumentPreviewArtifact} rows for the
     * given {@link DocumentFile}, joined to the caller's REQUIRED
     * transaction via {@link Propagation#MANDATORY}.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>{@code documentFile == null} → no persistence.</li>
     *   <li>Extension classifies to neither DOC nor DOCX → no persistence.</li>
     *   <li>Free DOC or DOCX ({@code paid == false}) → exactly one FULL
     *       PENDING artifact.</li>
     *   <li>Paid DOC or DOCX ({@code paid == true}) → exactly TWO
     *       PENDING artifacts:
     *       <ol>
     *         <li>FULL, with {@code documentFileId}, {@code
     *             sourceChecksumSha256}, and {@code variantVersion =
     *             INITIAL_VARIANT_VERSION};</li>
     *         <li>LIMITED, with the same {@code documentFileId}, {@code
     *             sourceChecksumSha256}, and {@code variantVersion =
     *             INITIAL_VARIANT_VERSION}.</li>
     *       </ol>
     *   </li>
     * </ul>
     *
     * <p>Idempotency: an existing artifact for the business-key tuple
     * is left untouched; the factory NEVER deletes, mutates, or
     * re-issues an artifact row that already exists. Re-entrant calls,
     * transactional retries, or duplicate approval re-entries yield the
     * original artifact set.</p>
     *
     * <p>Both rows commit or roll back together with the enclosing
     * Document, DocumentFile, and tag rows; on a save failure the JPA
     * transaction template will roll back the LIMIT CREATE.</p>
     *
     * <p>The factory stamps {@code createdAt}, {@code updatedAt} and
     * {@code nextAttemptAt} from the application {@link Clock} so the
     * artifact is immediately claimable by the production worker.</p>
     *
     * <p>The method MUST run inside an existing transaction
     * (MANDATORY propagation). It MUST NOT spawn a new transaction,
     * MUST NOT call any remote service, MUST NOT publish events,
     * MUST NOT start threads, MUST NOT call {@code exists(...)} or
     * {@code saveAndFlush(...)}, and MUST NOT catch
     * {@code DataIntegrityViolationException}.</p>
     *
     * @param documentFile the persisted primary DocumentFile; may be
     *                     {@code null}
     * @param paid         {@code true} for paid DOC/DOCX (must create
     *                     FULL + LIMITED), {@code false} for free
     *                     DOC/DOCX (must create FULL only)
     */
    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = false
    )
    public void bootstrapInsideTransaction(
            DocumentFile documentFile, boolean paid) {
        if (documentFile == null) {
            return;
        }

        AllowedDocumentFileType type = AllowedDocumentFileType
                .fromExtension(documentFile.getFileExtension())
                .orElse(null);

        if (type != AllowedDocumentFileType.DOC
                && type != AllowedDocumentFileType.DOCX) {
            return;
        }

        UUID documentFileId = documentFile.getId();
        String checksum = documentFile.getChecksumSha256();
        int variantVersion = INITIAL_VARIANT_VERSION;

        // Use the application clock so the factory's "now" agrees with
        // the worker's claim-predicate "now". The application clock is
        // wired to Clock.systemUTC() in ApplicationClockConfig, so on
        // ANY JVM (Asia/Ho_Chi_Minh, UTC, America/New_York) the
        // stamped nextAttemptAt is the same UTC instant, and the SQL
        // Server datetime2 column stores a region-independent value.
        LocalDateTime now = LocalDateTime.now(clock);

        if (!existsArtifact(documentFileId,
                DocumentPreviewArtifactKind.FULL, checksum, variantVersion)) {
            DocumentPreviewArtifact full = DocumentPreviewArtifact.builder()
                    .documentFileId(documentFileId)
                    .artifactKind(DocumentPreviewArtifactKind.FULL)
                    .sourceChecksumSha256(checksum)
                    .variantVersion(variantVersion)
                    .status(DocumentPreviewArtifactStatus.PENDING)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .createdAt(now)
                    .updatedAt(now)
                    .nextAttemptAt(now)
                    .build();
            artifactRepository.save(full);
        }

        if (paid && !existsArtifact(documentFileId,
                DocumentPreviewArtifactKind.LIMITED, checksum, variantVersion)) {
            DocumentPreviewArtifact limited = DocumentPreviewArtifact.builder()
                    .documentFileId(documentFileId)
                    .artifactKind(DocumentPreviewArtifactKind.LIMITED)
                    .sourceChecksumSha256(checksum)
                    .variantVersion(variantVersion)
                    .status(DocumentPreviewArtifactStatus.PENDING)
                    .attemptCount(0)
                    .maxAttempts(5)
                    .createdAt(now)
                    .updatedAt(now)
                    .nextAttemptAt(now)
                    .build();
            artifactRepository.save(limited);
        }
    }

    /**
     * Idempotency probe. Returns {@code true} when a matching artifact
     * already exists for the given business-key tuple. The probe covers
     * both the checksummed and the legacy null-checksum shapes so a
     * duplicate bootstrap call is correctly recognised even on legacy
     * sources whose {@code checksum_sha256} is {@code NULL}.
     */
    private boolean existsArtifact(UUID documentFileId,
                                   DocumentPreviewArtifactKind kind,
                                   String checksum,
                                   int variantVersion) {
        if (checksum != null && !checksum.isBlank()) {
            return artifactRepository
                    .findByDocumentFileIdAndArtifactKindAndSourceChecksumSha256AndVariantVersion(
                            documentFileId, kind, checksum, variantVersion)
                    .isPresent();
        }
        return artifactRepository
                .findFirstByDocumentFileIdAndArtifactKindAndSourceChecksumSha256IsNullAndVariantVersionOrderByCreatedAtDesc(
                        documentFileId, kind, variantVersion)
                .isPresent();
    }
}
