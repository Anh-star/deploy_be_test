package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.admin.document.AdminPendingDocumentsPageResponseDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminDetailDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminStatusPatchRequestDto;
import com.cmcu.itstudy.dto.document.DocumentPreviewStatusDto;
import com.cmcu.itstudy.entity.User;

import java.util.UUID;

public interface AdminDocumentService {

    AdminPendingDocumentsPageResponseDto listPendingDocuments(int page, int size);

    DocumentAdminDetailDto getDocumentDetail(UUID documentId);

    void updateDocumentStatus(UUID documentId, DocumentAdminStatusPatchRequestDto request, User moderator);

    /**
     * Returns the current async Office-to-PDF preview status for the
     * given document. Used by the frontend moderator review page to
     * decide when to enable the approve button for Office documents.
     *
     * <p>The document must exist and belong to an authenticated moderator.
     * Throws {@link java.util.NoSuchElementException} when the document
     * does not exist.</p>
     *
     * @param documentId the document UUID
     * @return the preview status snapshot
     */
    DocumentPreviewStatusDto getDocumentPreviewStatus(UUID documentId);
}
