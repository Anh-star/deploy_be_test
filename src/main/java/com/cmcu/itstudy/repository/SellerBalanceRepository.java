package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.SellerBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerBalanceRepository extends JpaRepository<SellerBalance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sb FROM SellerBalance sb WHERE sb.sellerId = :sellerId")
    Optional<SellerBalance> findBySellerIdForUpdate(@Param("sellerId") UUID sellerId);
}