package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.DocumentCardDto;
import com.cmcu.itstudy.dto.document.DocumentCreateRequestDto;
import com.cmcu.itstudy.entity.User;

import java.util.UUID;

/**
 * Non-transactional command router for the
 * {@code POST /api/my-documents} endpoint.
 *
 * <p>The controller calls this bean instead of a {@code @Transactional}
 * method that wraps a remote Supabase call. The router runs WITHOUT any
 * database transaction so:
 * <ul>
 *   <li>FREE create delegates straight to
 *       {@link TransactionalDocumentCrudService#createFreeDocument}
 *       (which opens its own {@code REQUIRED} transaction).</li>
 *   <li>PAID create delegates to
 *       {@link PaidDocumentUploadOrchestrator#orchestratePaidCreate},
 *       which itself is {@code @Transactional(NOT_SUPPORTED)} and
 *       composes a remote Supabase call with a sub-transactional
 *       binder.</li>
 * </ul>
 *
 * <p>The router NEVER opens a transaction itself. It performs no
 * {@code @Transactional} self-invocation tricks and does NOT use
 * {@link org.springframework.transaction.interceptor.TransactionAspectSupport}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>decide between the two branches based on
 *       {@link DocumentCreateRequestDto#getIsPaid()};</li>
 *   <li>guard cross-shape preconditions (PAID requires {@code uploadId};
 *       FREE rejects {@code uploadId}) so the DTO-level invariants are
 *       reinforced at the service boundary;</li>
 *   <li>delegate to one transactional service and return its mapped
 *       response.</li>
 * </ul>
 */
public interface DocumentCommandRouter {

    /**
     * Route the create-document command to the appropriate
     * transaction-scoped service. The router itself does NOT open a
     * transaction.
     *
     * @param request      the validated create payload
     * @param currentUserId authenticated user id (the entity is loaded
     *                     by the sub-services)
     * @return mapped card DTO
     */
    DocumentCardDto routeCreate(
            DocumentCreateRequestDto request,
            UUID currentUserId);

    /**
     * Convenience overload for controller code that already has the
     * authenticated {@link User} on hand.
     */
    default DocumentCardDto routeCreate(
            DocumentCreateRequestDto request,
            User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new IllegalArgumentException("currentUser must not be null");
        }
        return routeCreate(request, currentUser.getId());
    }
}
