package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.document.DocumentUpdateRequestDto;
import com.cmcu.itstudy.dto.document.MyDocumentAutoQuizCreateRequestDto;
import com.cmcu.itstudy.dto.document.MyDocumentAutoQuizDto;
import com.cmcu.itstudy.dto.document.MyDocumentDetailDto;
import com.cmcu.itstudy.dto.document.MyDocumentQuizListDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.User;

import java.util.List;
import java.util.UUID;

public interface DocumentService {

    Document getById(UUID id);

    List<Document> getRelatedDocuments(UUID documentId, int limit);

    DocumentCardDto createDocument(DocumentCreateRequestDto documentCreateRequestDto, User currentUser);

    DocumentCardDto updateDocument(UUID documentId, DocumentUpdateRequestDto documentUpdateRequestDto, User currentUser);

    void deleteDocument(UUID documentId, User currentUser);

    List<DocumentCardDto> getMyDocuments(User currentUser);

    MyDocumentDetailDto getMyDocumentDetail(UUID documentId, User currentUser);

    MyDocumentAutoQuizDto getMyDocumentAutoQuiz(UUID documentId, User currentUser);

    /**
     * Return every AI quiz generation attached to the supplied document,
     * newest-first. Empty list means the document has never been enqueued.
     * Only the document owner may call this.
     */
    List<MyDocumentAutoQuizDto> getMyDocumentAutoQuizzes(UUID documentId, User currentUser);

    /**
     * Enqueue a brand-new AI quiz generation for the supplied document.
     * Phase Multi Auto Quiz 2: each call creates an independent generation;
     * there is no reuse or limit.
     *
     * @throws NoSuchElementException if the document does not exist or is
     *         soft-deleted
     * @throws AccessDeniedException  if the caller is not the document owner
     * @throws IllegalArgumentException if the primary file type is unsupported
     */
    MyDocumentAutoQuizDto createMyDocumentAutoQuiz(
            UUID documentId,
            MyDocumentAutoQuizCreateRequestDto request,
            User currentUser);

    MyDocumentQuizListDto getMyDocumentQuizzes(int page, int size, User currentUser);

    void reportDocument(UUID documentId, User reporter, com.cmcu.itstudy.dto.document.DocumentReportRequestDto requestDto);

    org.springframework.data.domain.Page<com.cmcu.itstudy.dto.document.DocumentReportResponseDto> getReportedDocuments(String status, int page, int size);

    void resolveReport(UUID reportId, User resolver);

    void dismissReport(UUID reportId, User resolver);
}

