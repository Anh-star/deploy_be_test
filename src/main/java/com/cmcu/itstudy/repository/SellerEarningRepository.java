package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.SellerEarning;
import com.cmcu.itstudy.enums.SellerEarningStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerEarningRepository extends JpaRepository<SellerEarning, UUID> {

    Optional<SellerEarning> findByPaymentId(UUID paymentId);

    Page<SellerEarning> findBySellerIdOrderByCreatedAtDesc(UUID sellerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT se FROM SellerEarning se WHERE se.id = :earningId")
    Optional<SellerEarning> findByIdForUpdate(@Param("earningId") UUID earningId);

    @Transactional(readOnly = true)
    @Query("""
        SELECT se.id
        FROM SellerEarning se
        WHERE se.status = :status
          AND se.availableAt <= :now
        ORDER BY se.availableAt ASC, se.id ASC
    """)
    List<UUID> findDueEarningIds(
            @Param("status") SellerEarningStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}