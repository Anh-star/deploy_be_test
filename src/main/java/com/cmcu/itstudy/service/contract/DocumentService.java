package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.dto.document.DocumentUpdateRequestDto;
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

    MyDocumentQuizListDto getMyDocumentQuizzes(int page, int size, User currentUser);

    void reportDocument(UUID documentId, User reporter, com.cmcu.itstudy.dto.document.DocumentReportRequestDto requestDto);

    org.springframework.data.domain.Page<com.cmcu.itstudy.dto.document.DocumentReportResponseDto> getReportedDocuments(String status, int page, int size);

    void resolveReport(UUID reportId, User resolver);

    void dismissReport(UUID reportId, User resolver);
}

