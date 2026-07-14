package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.SellerEarning;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerEarningRepository extends JpaRepository<SellerEarning, UUID> {

    Optional<SellerEarning> findByPaymentId(UUID paymentId);

    Page<SellerEarning> findBySellerIdOrderByCreatedAtDesc(UUID sellerId, Pageable pageable);
}
