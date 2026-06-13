package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.DocumentAccess;
import com.cmcu.itstudy.repository.DocumentAccessRepository;
import com.cmcu.itstudy.service.contract.DocumentAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DocumentAccessServiceImpl implements DocumentAccessService {

    private final DocumentAccessRepository documentAccessRepository;

    public DocumentAccessServiceImpl(DocumentAccessRepository documentAccessRepository) {
        this.documentAccessRepository = documentAccessRepository;
    }

    @Override
public boolean hasAccess(UUID userId, UUID documentId) {

    System.out.println("===== ACCESS CHECK =====");
    System.out.println("USER ID = " + userId);
    System.out.println("DOC ID  = " + documentId);

    boolean result =
            documentAccessRepository
                    .existsByUserIdAndDocumentId(userId, documentId);

    System.out.println("RESULT = " + result);

    return result;
}

    @Override
    @Transactional
    public void grantAccess(UUID userId, UUID documentId, UUID paymentId) {
        if (documentAccessRepository.existsByUserIdAndDocumentId(userId, documentId)) {
            return;
        }
        DocumentAccess access = DocumentAccess.builder()
                .userId(userId)
                .documentId(documentId)
                .paymentId(paymentId)
                .grantedAt(LocalDateTime.now())
                .build();
        documentAccessRepository.save(access);
    }
}
