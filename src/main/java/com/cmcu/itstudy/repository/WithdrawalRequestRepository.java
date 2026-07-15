package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.WithdrawalRequest;
import jakarta.persistence.LockModeType;
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
}