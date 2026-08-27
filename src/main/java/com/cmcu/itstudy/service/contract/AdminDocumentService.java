package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.admin.document.AdminPendingDocumentsPageResponseDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminDetailDto;
import com.cmcu.itstudy.dto.admin.document.DocumentAdminStatusPatchRequestDto;
import com.cmcu.itstudy.dto.document.DocumentPreviewStatusDto;
import com.cmcu.itstudy.entity.User;

import java.util.UUID;

public interface AdminDocumentService {

    AdminPendingDocumentsPageResponseDto listPendingDocuments(String status, String search, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, int page, int size);

    default AdminPendingDocumentsPageResponseDto listPendingDocuments(String status, int page, int size) {
        return listPendingDocuments(status, null, null, null, page, size);
    }

    default AdminPendingDocumentsPageResponseDto listPendingDocuments(int page, int size) {
        return listPendingDocuments(null, null, null, null, page, size);
    }

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
