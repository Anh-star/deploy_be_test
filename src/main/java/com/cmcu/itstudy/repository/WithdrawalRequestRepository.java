package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.WithdrawalRequest;
import com.cmcu.itstudy.enums.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT wr
        FROM WithdrawalRequest wr
        WHERE wr.id = :withdrawalId
    """)
    Optional<WithdrawalRequest> findByIdForUpdate(@Param("withdrawalId") UUID withdrawalId);

    Optional<WithdrawalRequest> findBySellerIdAndClientRequestId(
            UUID sellerId,
            UUID clientRequestId
    );

    @Query(
            value = """
                    SELECT wr
                    FROM WithdrawalRequest wr
                    LEFT JOIN User u ON u.id = wr.sellerId
                    WHERE (:status IS NULL OR wr.status = :status)
                      AND (
                            :search IS NULL
                            OR LOWER(wr.requestCode)
                                LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(u.email)
                                LIKE LOWER(CONCAT('%', :search, '%'))
                            OR (
                                u.fullName IS NOT NULL
                                AND LOWER(u.fullName)
                                    LIKE LOWER(CONCAT('%', :search, '%'))
                            )
                      )
                      AND (:startDate IS NULL OR wr.createdAt >= :startDate)
                      AND (:endDate IS NULL OR wr.createdAt <= :endDate)
                    """,
            countQuery = """
                    SELECT COUNT(wr)
                    FROM WithdrawalRequest wr
                    LEFT JOIN User u ON u.id = wr.sellerId
                    WHERE (:status IS NULL OR wr.status = :status)
                      AND (
                            :search IS NULL
                            OR LOWER(wr.requestCode)
                                LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(u.email)
                                LIKE LOWER(CONCAT('%', :search, '%'))
                            OR (
                                u.fullName IS NOT NULL
                                AND LOWER(u.fullName)
                                    LIKE LOWER(CONCAT('%', :search, '%'))
                            )
                      )
                      AND (:startDate IS NULL OR wr.createdAt >= :startDate)
                      AND (:endDate IS NULL OR wr.createdAt <= :endDate)
                    """
    )
    Page<WithdrawalRequest> searchForPaymentModerator(
            @Param("status") WithdrawalStatus status,
            @Param("search") String search,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            Pageable pageable
    );

    default Page<WithdrawalRequest> searchForPaymentModerator(
            WithdrawalStatus status,
            String search,
            Pageable pageable
    ) {
        return searchForPaymentModerator(status, search, null, null, pageable);
    }

    Page<WithdrawalRequest> findAllBySellerId(
            UUID sellerId,
            Pageable pageable
    );

    Page<WithdrawalRequest> findAllBySellerIdAndStatus(
            UUID sellerId,
            WithdrawalStatus status,
            Pageable pageable
    );

    Optional<WithdrawalRequest> findByIdAndSellerId(
            UUID withdrawalId,
            UUID sellerId
    );
}
