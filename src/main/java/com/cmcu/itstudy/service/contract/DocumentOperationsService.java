package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.DocumentCardResponseDto;
import com.cmcu.itstudy.dto.document.DocumentListRequestDto;
import com.cmcu.itstudy.dto.document.PagedResponseDocumentCardDto;

import java.util.List;
import java.util.UUID;

public interface DocumentOperationsService {

    PagedResponseDocumentCardDto searchDocuments(DocumentListRequestDto request, UUID currentUserId);

    void increaseView(UUID documentId, UUID userId);

    void downloadDocument(UUID documentId, UUID userId);

    void toggleBookmark(UUID documentId, UUID userId);

    List<DocumentCardResponseDto> getMyBookmarks(UUID userId);

    PagedResponseDocumentCardDto getMyViewHistory(int page, int size, UUID currentUserId);
}
