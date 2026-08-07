package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.service.contract.DocumentPreviewSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only implementation of {@link DocumentPreviewSnapshotService}.
 *
 * <p>The method runs in a {@code @Transactional(readOnly = true)} block
 * so the JPA session is closed BEFORE the preview service issues the
 * Supabase HTTP call. This is a deliberate split: the snapshot fetch
 * touches the database; the bytes fetch touches Supabase; the two
 * stages MUST NOT overlap.
 */
@Service
public class DocumentPreviewSnapshotServiceImpl implements DocumentPreviewSnapshotService {

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;

    public DocumentPreviewSnapshotServiceImpl(DocumentRepository documentRepository,
                                              DocumentFileRepository documentFileRepository) {
        this.documentRepository = documentRepository;
        this.documentFileRepository = documentFileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentPreviewSnapshot> resolve(UUID documentId) {
        Optional<Document> documentOpt = documentRepository.findByIdAndDeletedFalse(documentId);
        if (documentOpt.isEmpty()) {
            return Optional.empty();
        }
        Document document = documentOpt.get();
        Optional<DocumentFile> primary = documentFileRepository.findByDocumentIdAndPrimaryTrue(documentId);
        if (primary.isEmpty()) {
            // No primary file → caller decides what to render (free
            // docs may legitimately have no DocumentFile row when the
            // public URL lives on Document.fileUrl).
            return Optional.of(DocumentPreviewSnapshot.fromDocument(
                    document, null, null, document.getFileType() != null
                            ? document.getFileType().name()
                            : null, null));
        }
        DocumentFile file = primary.get();
        String mimeType = file.getMimeType();
        if (mimeType == null && file.getFileExtension() != null) {
            mimeType = "application/" + file.getFileExtension().toLowerCase();
        }
        return Optional.of(DocumentPreviewSnapshot.fromDocument(
                document, file.getStorageBucket(), file.getStoragePath(), mimeType,
                file.getFileExtension()));
    }
}