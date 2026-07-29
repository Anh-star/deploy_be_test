package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.Payment;
import com.cmcu.itstudy.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderCode(String orderCode);

    List<Payment> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Payment> findByOrderCodeAndUserId(String orderCode, UUID userId);

    /**
     * True iff there exists a payment on {@code documentId} with the given
     * {@link PaymentStatus} whose buyer is not {@code excludedUserId}.
     *
     * <p>Used by the pricing-lock guard in {@code DocumentServiceImpl}: a
     * document is locked once any non-owner buyer has successfully paid for
     * it. Self-purchase rows (if any historical data ever exists) are excluded
     * here rather than relying solely on the create-payment guard, so legacy
     * or anomalous data cannot unlock pricing.
     */
    boolean existsByDocumentIdAndStatusAndUserIdNot(
            UUID documentId,
            PaymentStatus status,
            UUID excludedUserId);

    /**
     * Counts payment rows on {@code documentId} with the given
     * {@link PaymentStatus} whose buyer is not {@code excludedUserId}.
     *
     * <p>Surfaces the "successful purchase count" on the owner detail DTO.
     * Distinct-buyer semantics are NOT collapsed here — see
     * {@code MyDocumentDetailDto} documentation for the rationale. If the
     * business later needs distinct-buyer counting, add a dedicated
     * repository method rather than mutating this one.
     */
    long countByDocumentIdAndStatusAndUserIdNot(
            UUID documentId,
            PaymentStatus status,
            UUID excludedUserId);

    /**
     * Bulk query for the owner list: returns the distinct set of document ids
     * (within the supplied {@code documentIds} window) that already have at
     * least one payment with {@link PaymentStatus#SUCCESS} from a non-owner
     * buyer. The owner list uses the result to mark each card as
     * pricing-locked in a single round-trip instead of issuing N+1 existsBy
     * queries.
     */
    @Query("""
            SELECT DISTINCT p.documentId
            FROM Payment p
            WHERE p.documentId IN :documentIds
              AND p.status = :status
              AND p.userId <> :excludedUserId
            """)
    List<UUID> findDistinctDocumentIdsWithSuccessfulBuyer(
            @Param("documentIds") Collection<UUID> documentIds,
            @Param("status") PaymentStatus status,
            @Param("excludedUserId") UUID excludedUserId);
}
