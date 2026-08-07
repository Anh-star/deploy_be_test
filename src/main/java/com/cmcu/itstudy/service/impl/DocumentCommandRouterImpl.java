package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.DocumentCommandRouter;
import com.cmcu.itstudy.service.contract.PaidDocumentUploadOrchestrator;
import com.cmcu.itstudy.service.contract.TransactionalDocumentCrudService;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Default implementation of {@link DocumentCommandRouter}.
 *
 * <p>This bean is intentionally NOT {@code @Transactional}. Its only
 * job is to delegate to the correct transactional sub-service. The
 * {@code User} is loaded here so we can pass the entity down without
 * giving the router database-write responsibilities.
 *
 * <p>The router validates the FREE-vs-PAID shape one more time so a
 * misconfigured call from internal callers is rejected before it
 * reaches the orchestrator or the free service.
 */
@Service
public class DocumentCommandRouterImpl implements DocumentCommandRouter {

    private final UserRepository userRepository;
    private final TransactionalDocumentCrudService freeTransactionService;
    private final PaidDocumentUploadOrchestrator paidOrchestrator;

    public DocumentCommandRouterImpl(
            UserRepository userRepository,
            TransactionalDocumentCrudService freeTransactionService,
            PaidDocumentUploadOrchestrator paidOrchestrator) {
        this.userRepository = userRepository;
        this.freeTransactionService = freeTransactionService;
        this.paidOrchestrator = paidOrchestrator;
    }

    @Override
    public DocumentCardDto routeCreate(DocumentCreateRequestDto request, UUID currentUserId) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (currentUserId == null) {
            throw new IllegalArgumentException("currentUserId must not be null");
        }
        if (request.getIsPaid() == null) {
            throw new IllegalArgumentException("isPaid must not be null");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Authenticated user not found: " + currentUserId));

        if (Boolean.TRUE.equals(request.getIsPaid())) {
            if (request.getUploadId() == null) {
                // Defense-in-depth — the DTO cross-field rule already
                // rejects missing uploadId for PAID. This guard fires
                // only on a programming error.
                throw new IllegalArgumentException(
                        "Paid create requires uploadId");
            }
            return paidOrchestrator.orchestratePaidCreate(request, currentUser);
        }

        // FREE branch.
        if (request.getUploadId() != null) {
            // FREE create must NOT supply uploadId; the DTO already
            // enforces this via the cross-field rule. This is a
            // belt-and-suspenders guard for internal callers.
            throw new IllegalArgumentException(
                    "Free create must not include uploadId");
        }
        return freeTransactionService.createFreeDocument(request, currentUser);
    }
}
