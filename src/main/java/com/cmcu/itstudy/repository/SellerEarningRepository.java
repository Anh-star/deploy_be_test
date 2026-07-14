package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.SellerEarning;
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
public interface SellerEarningRepository extends JpaRepository<SellerEarning, UUID> {

    Optional<SellerEarning> findByPaymentId(UUID paymentId);

    Page<SellerEarning> findBySellerIdOrderByCreatedAtDesc(UUID sellerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT se FROM SellerEarning se WHERE se.id = :earningId")
    Optional<SellerEarning> findByIdForUpdate(@Param("earningId") UUID earningId);
}