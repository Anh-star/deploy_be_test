package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentPreviewArtifactStatusDto;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.DocumentPreviewArtifact;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactKind;
import com.cmcu.itstudy.enums.DocumentPreviewArtifactStatus;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentPreviewArtifactRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.service.contract.DocumentPreviewStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Default read-only implementation of {@link DocumentPreviewStateService}.
 *
 * <p>The service mirrors {@link com.cmcu.itstudy.service.impl.AdminDocumentServiceImpl}'s
 * preview-status resolution logic but exposes only the safe, frontend-facing
 * fields used by {@code GET /api/documents/{id}/preview} when async Office
 * preview is enabled.</p>
 */
@Service
public class DocumentPreviewStateServiceImpl implements DocumentPreviewStateService {

    private static final String PENDING_MESSAGE =
            "Đang chờ tạo bản xem trước";
    private static final String PROCESSING_MESSAGE =
            "Đang chuyển đổi DOC/DOCX sang PDF";
    private static final String RETRY_MESSAGE =
            "Hệ thống đang thử xử lý lại";
    private static final String DEAD_MESSAGE =
            "Không thể tạo bản xem trước";

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final DocumentPreviewArtifactRepository artifactRepository;

    public DocumentPreviewStateServiceImpl(DocumentRepository documentRepository,
                                           DocumentFileRepository documentFileRepository,
                                           DocumentPreviewArtifactRepository artifactRepository) {
        this.documentRepository = documentRepository;
        this.documentFileRepository = documentFileRepository;
        this.artifactRepository = artifactRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PreviewState resolve(UUID documentId) {
        boolean documentExists = documentRepository.findById(documentId).isPresent();
        if (!documentExists) {
            return PreviewState.nonOffice();
        }

        Optional<DocumentFile> primaryFile =
                documentFileRepository.findByDocumentIdAndPrimaryTrue(documentId);
        if (primaryFile.isEmpty()) {
            return PreviewState.nonOffice();
        }

        DocumentFile file = primaryFile.get();
        AllowedDocumentFileType fileType =
                AllowedDocumentFileType.fromExtension(file.getFileExtension()).orElse(null);
        boolean isOffice = fileType == AllowedDocumentFileType.DOC
                || fileType == AllowedDocumentFileType.DOCX;
        if (!isOffice) {
            return PreviewState.nonOffice();
        }

        Optional<DocumentPreviewArtifact> artifact =
                artifactRepository.findFirstByDocumentFileIdAndArtifactKindOrderByCreatedAtDescIdDesc(
                        file.getId(), DocumentPreviewArtifactKind.FULL);

        if (artifact.isEmpty()) {
            return PreviewState.waiting(DocumentPreviewArtifactStatusDto.PENDING,
                    PENDING_MESSAGE, true);
        }

        DocumentPreviewArtifactStatus status = artifact.get().getStatus();
        DocumentPreviewArtifactStatusDto dtoStatus = mapStatus(status);
        return PreviewState.waiting(dtoStatus, messageFor(status), isRetryable(status));
    }

    private static DocumentPreviewArtifactStatusDto mapStatus(DocumentPreviewArtifactStatus status) {
        if (status == null) {
            return DocumentPreviewArtifactStatusDto.PENDING;
        }
        return switch (status) {
            case PENDING -> DocumentPreviewArtifactStatusDto.PENDING;
            case PROCESSING -> DocumentPreviewArtifactStatusDto.PROCESSING;
            case READY -> DocumentPreviewArtifactStatusDto.READY;
            case RETRY -> DocumentPreviewArtifactStatusDto.RETRY;
            case DEAD -> DocumentPreviewArtifactStatusDto.DEAD;
        };
    }

    private static String messageFor(DocumentPreviewArtifactStatus status) {
        if (status == null) {
            return PENDING_MESSAGE;
        }
        return switch (status) {
            case PENDING -> PENDING_MESSAGE;
            case PROCESSING -> PROCESSING_MESSAGE;
            case RETRY -> RETRY_MESSAGE;
            case READY -> PENDING_MESSAGE;
            case DEAD -> DEAD_MESSAGE;
        };
    }

    private static boolean isRetryable(DocumentPreviewArtifactStatus status) {
        return status == null
                || status == DocumentPreviewArtifactStatus.PENDING
                || status == DocumentPreviewArtifactStatus.PROCESSING
                || status == DocumentPreviewArtifactStatus.RETRY;
    }
}
