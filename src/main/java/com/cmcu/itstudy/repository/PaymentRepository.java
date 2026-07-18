package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderCode(String orderCode);

    List<Payment> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Payment> findByOrderCodeAndUserId(String orderCode, UUID userId);
}
