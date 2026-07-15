package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.SellerPayoutProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerPayoutProfileRepository
        extends JpaRepository<SellerPayoutProfile, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT spp
        FROM SellerPayoutProfile spp
        WHERE spp.sellerId = :sellerId
    """)
    Optional<SellerPayoutProfile> findBySellerIdForUpdate(
            @Param("sellerId") UUID sellerId
    );
}