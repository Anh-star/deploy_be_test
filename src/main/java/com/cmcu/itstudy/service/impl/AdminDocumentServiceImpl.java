package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.admin.document.AdminPendingDocumentsPageResponseDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminDetailDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminStatusPatchRequestDto;
import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentPreviewArtifactStatusDto;
import com.cmcu.itstudy.dto.document.DocumentPreviewStatusDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.DocumentPreviewArtifact;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.NotificationType;
import com.cmcu.itstudy.handle.PreviewNotReadyException;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.service.contract.AdminDocumentService;
import com.cmcu.itstudy.service.contract.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminDocumentServiceImpl implements AdminDocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentPreviewArtifactRepository artifactRepository;
    private final DocumentPreviewArtifactFactory artifactFactory;
    private final Clock clock;
    private final NotificationService notificationService;

    public AdminDocumentServiceImpl(DocumentRepository documentRepository,
                                    DocumentFileRepository documentFileRepository,
                                    DocumentPreviewArtifactRepository artifactRepository,
                                    DocumentPreviewArtifactFactory artifactFactory,
                                    Clock clock,
                                    NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.documentFileRepository = documentFileRepository;
        this.artifactRepository = artifactRepository;
        this.artifactFactory = Objects.requireNonNull(artifactFactory, "artifactFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentAdminDetailDto getDocumentDetail(UUID documentId) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
        String previewUrl = resolvePreviewFileUrl(document);
        String storagePath = documentFileRepository.findByDocumentIdAndPrimaryTrue(documentId)
                .map(DocumentFile::getStoragePath)
                .orElse(null);
        return DocumentAdminDetailDto.builder()
                .id(document.getId() != null ? document.getId().toString() : null)
                .title(document.getTitle())
                .description(document.getDescription())
                .fileUrl(previewUrl)
                .thumbnailUrl(document.getThumbnailUrl())
                .fileType(document.getFileType() != null ? document.getFileType().name() : null)
                .fileName(document.getFileName())
                .fileSizeBytes(document.getFileSize())
                .authorName(document.getCreatedBy() != null ? document.getCreatedBy().getFullName() : null)
                .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                .status(document.getStatus())
                .createdAt(document.getCreatedAt())
                .rejectReason(document.getRejectReason())
                .storagePath(storagePath)
                .build();
    }

    /**
     * Ưu tiên URL công khai từ DocumentFile (fileUrl), sau đó storagePath nếu là URL đầy đủ,
     * cuối cùng {@link Document#getFileUrl()} (Supabase public URL từ luồng upload).
     */
    private String resolvePreviewFileUrl(Document document) {
        return documentFileRepository.findByDocumentIdAndPrimaryTrue(document.getId())
                .map(this::previewUrlFromPrimaryFile)
                .filter(StringUtils::hasText)
                .orElseGet(() -> StringUtils.hasText(document.getFileUrl()) ? document.getFileUrl().trim() : null);
    }

    private String previewUrlFromPrimaryFile(DocumentFile file) {
        if (StringUtils.hasText(file.getFileUrl()) && isHttpUrl(file.getFileUrl())) {
            return file.getFileUrl().trim();
        }
        if (StringUtils.hasText(file.getStoragePath()) && isHttpUrl(file.getStoragePath())) {
            return file.getStoragePath().trim();
        }
        return null;
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return v.startsWith("https://") || v.startsWith("http://");
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPendingDocumentsPageResponseDto listPendingDocuments(String status, int page, int size) {
        int p = Math.max(0, page);
        int s = size < 1 ? 10 : Math.min(size, 100);

        DocumentStatus docStatus = null;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            try {
                docStatus = DocumentStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // If invalid status string, docStatus stays null
            }
        }

        Page<Document> result;
        if (docStatus != null) {
            result = documentRepository.findPendingPageWithCategoryAndCreator(
                    docStatus,
                    PageRequest.of(p, s)
            );
        } else {
            result = documentRepository.findAllPageWithCategoryAndCreator(
                    PageRequest.of(p, s)
            );
        }

        List<DocumentCardDto> content = result.getContent().stream()
                .map(this::toPendingCardDto)
                .collect(Collectors.toList());
        return AdminPendingDocumentsPageResponseDto.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private DocumentCardDto toPendingCardDto(Document d) {
        return DocumentCardDto.builder()
                .id(d.getId() != null ? d.getId().toString() : null)
                .title(d.getTitle())
                .slug(d.getSlug())
                .description(d.getDescription())
                .thumbnailUrl(d.getThumbnailUrl())
                .fileName(d.getFileName())
                .fileType(d.getFileType() != null ? d.getFileType().name() : null)
                .fileSize(d.getFileSize())
                .status(d.getStatus())
                .uploadDate(d.getCreatedAt())
                .views(d.getViewCount())
                .downloads(d.getDownloadCount())
                .bookmarks(d.getBookmarkCount())
                .categoryName(d.getCategory() != null ? d.getCategory().getName() : null)
                .authorName(d.getCreatedBy() != null ? d.getCreatedBy().getFullName() : null)
                .tags(null)
                .documentUrl(d.getFileUrl())
                .storagePath(null)
                .build();
    }

    @Override
    @Transactional
    public void updateDocumentStatus(UUID documentId, DocumentAdminStatusPatchRequestDto request, User moderator) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        if (Boolean.TRUE.equals(document.getDeleted())) {
            throw new IllegalStateException("Document is deleted");
        }
        if (document.getStatus() != DocumentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING documents can be approved or rejected");
        }

        DocumentStatus target = request.getStatus();
        if (target != DocumentStatus.APPROVED && target != DocumentStatus.REJECTED) {
            throw new IllegalArgumentException("status must be APPROVED or REJECTED");
        }

        if (target == DocumentStatus.REJECTED) {
            if (!StringUtils.hasText(request.getRejectReason())) {
                throw new IllegalArgumentException("rejectReason is required when rejecting");
            }
            document.setRejectReason(request.getRejectReason().trim());
        } else {
            // APPROVED:
            //
            // (a) Idempotent preview artifact bootstrap, joining the
            //     caller's REQUIRED transaction via MANDATORY propagation.
            //     The factory enforces all guards itself:
            //       * file is DOC or DOCX;
            //       * paid documents get FULL + LIMITED;
            //       * free documents get FULL only;
            //       * non-Office files are a no-op;
            //       * existing FULL/LIMITED rows are NOT duplicated.
            //     We pass document.isPaid() (NOT a hard-coded true), so a
            //     free DOCX is correctly given just one FULL and a paid
            //     DOCX is given FULL + LIMITED.
            ensurePreviewArtifactsPresent(document);

            // (b) Guard Office documents until FULL preview is READY.
            guardOfficePreviewReady(documentId);
            document.setRejectReason(null);
            document.setPublishedAt(LocalDateTime.now(clock));
        }

        document.setStatus(target);
        document.setUpdatedBy(moderator);
        Document savedDoc = documentRepository.save(document);

        notifyDocumentModeration(
                savedDoc,
                moderator,
                target,
                target == DocumentStatus.APPROVED ? request.getAdminNote() : savedDoc.getRejectReason()
        );
    }

    /**
     * Ensures the preview-artifact set for the given document's primary
     * {@link DocumentFile} is initialised.
     *
     * <p>This is the approval-side safety net for the preview pipeline.
     * In normal operation the upload/bind path already calls
     * {@link DocumentPreviewArtifactFactory#bootstrapInsideTransaction(
     * DocumentFile, boolean)} with the correct {@code paid} flag, but if
     * that earlier bootstrap failed silently (network error, partial
     * rollback, manual DB intervention), a moderator must still be able
     * to rescue the document by re-issuing the bootstrap here. The
     * factory is idempotent: an existing
     * {@code (documentFileId, artifactKind, sourceChecksumSha256,
     * variantVersion)} row is never duplicated.</p>
     *
     * <p>The {@code paid} flag is taken from
     * {@link Document#getIsPaid()} &mdash; never hard-coded &mdash; so
     * the artifact set matches the document's actual pricing shape:</p>
     *
     * <ul>
     *   <li>free DOC / DOCX &rarr; exactly one FULL artifact;</li>
     *   <li>paid DOC / DOCX &rarr; one FULL plus one LIMITED artifact;</li>
     *   <li>non-Office files &rarr; no-op inside the factory.</li>
     * </ul>
     *
     * <p>This method never throws for missing primary files or unknown
     * extensions; the factory's own guards handle the rejection logic
     * and we let the existing {@link #guardOfficePreviewReady(UUID)}
     * raise a meaningful error if the artifact is still not READY.</p>
     *
     * @param document the document being approved
     */
    private void ensurePreviewArtifactsPresent(Document document) {
        if (document == null) {
            return;
        }
        Optional<DocumentFile> primaryFile =
                documentFileRepository.findByDocumentIdAndPrimaryTrue(document.getId());
        if (primaryFile.isEmpty()) {
            return;
        }
        boolean paid = Boolean.TRUE.equals(document.getIsPaid());
        artifactFactory.bootstrapInsideTransaction(primaryFile.get(), paid);
    }

    /**
     * Enforces the server-side rule: a DOC/DOCX document may not be
     * approved unless its FULL preview artifact is READY.
     *
     * <p>This method re-reads the current artifact state within the same
     * transaction that performs the approval, eliminating the race where
     * a frontend read of READY is followed by a state change before the
     * approval PATCH arrives.
     *
     * <p>Non-Office documents are not subject to this guard and pass
     * through without any check.</p>
     *
     * @param documentId the document UUID being approved
     * @throws PreviewNotReadyException when the document is Office and the
     *         FULL artifact is not READY
     */
    private void guardOfficePreviewReady(UUID documentId) {
        DocumentPreviewStatusDto previewStatus = getDocumentPreviewStatus(documentId);
        if (!previewStatus.isOfficeDocument()) {
            return;
        }
        DocumentPreviewArtifactStatusDto status = previewStatus.getFullStatus();
        if (status != DocumentPreviewArtifactStatusDto.READY) {
            throw new PreviewNotReadyException(
                    "Bản xem trước DOC/DOCX chưa sẵn sàng để phê duyệt.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentPreviewStatusDto getDocumentPreviewStatus(UUID documentId) {
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        Optional<DocumentFile> primaryFile =
                documentFileRepository.findByDocumentIdAndPrimaryTrue(documentId);

        if (primaryFile.isEmpty()) {
            return DocumentPreviewStatusDto.builder()
                    .officeDocument(false)
                    .build();
        }

        String ext = primaryFile.get().getFileExtension();
        AllowedDocumentFileType fileType =
                AllowedDocumentFileType.fromExtension(ext).orElse(null);

        boolean isOffice = (fileType == AllowedDocumentFileType.DOC
                || fileType == AllowedDocumentFileType.DOCX);

        if (!isOffice) {
            return DocumentPreviewStatusDto.builder()
                    .officeDocument(false)
                    .build();
        }

        Optional<DocumentPreviewArtifact> artifact =
                artifactRepository.findFirstByDocumentFileIdAndArtifactKindOrderByCreatedAtDescIdDesc(
                        primaryFile.get().getId(),
                        DocumentPreviewArtifactKind.FULL);

        if (artifact.isEmpty()) {
            return DocumentPreviewStatusDto.builder()
                    .officeDocument(true)
                    .fullStatus(DocumentPreviewArtifactStatusDto.PENDING)
                    .build();
        }

        DocumentPreviewArtifact a = artifact.get();
        return DocumentPreviewStatusDto.builder()
                .officeDocument(true)
                .fullStatus(mapStatus(a.getStatus()))
                .lastError(boundLastError(a.getLastError()))
                .attemptCount(a.getAttemptCount())
                .maxAttempts(a.getMaxAttempts())
                .pageCount(a.getTotalPages())
                .build();
    }

    private static DocumentPreviewArtifactStatusDto mapStatus(
            com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> DocumentPreviewArtifactStatusDto.PENDING;
            case PROCESSING -> DocumentPreviewArtifactStatusDto.PROCESSING;
            case READY -> DocumentPreviewArtifactStatusDto.READY;
            case RETRY -> DocumentPreviewArtifactStatusDto.RETRY;
            case DEAD -> DocumentPreviewArtifactStatusDto.DEAD;
        };
    }

    /**
     * Bounds the error message so no internal path, stack trace, or
     * credential fragment is exposed to the frontend.
     */
    private static String boundLastError(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Truncate to 120 characters — enough to surface a clear
        // operational code or short message; not enough for a stack dump.
        String trimmed = raw.trim();
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 120) + "…";
    }

    private void notifyDocumentModeration(Document doc, User moderator, DocumentStatus target, String customNote) {
        if (doc.getCreatedBy() == null || doc.getCreatedBy().getId() == null) {
            return;
        }
        try {
            String title = doc.getTitle() != null ? doc.getTitle() : "tài liệu";
            boolean isApproved = (target == DocumentStatus.APPROVED);
            String noteSuffix = (customNote != null && !customNote.isBlank())
                    ? (isApproved ? " Ghi chú: " : " Lý do: ") + customNote.trim()
                    : "";
            String msg = isApproved
                    ? "Tài liệu \"" + title + "\" của bạn đã được duyệt và xuất bản." + noteSuffix
                    : "Tài liệu \"" + title + "\" của bạn đã bị từ chối." + noteSuffix;

            notificationService.createAndPush(
                    doc.getCreatedBy().getId(),
                    moderator != null ? moderator.getId() : null,
                    isApproved ? NotificationType.DOCUMENT_APPROVED : NotificationType.DOCUMENT_REJECTED,
                    doc.getId().toString(),
                    "DOCUMENT",
                    msg
            );
        } catch (Exception ignored) {
            // Ignore notification failure
        }
    }
}
