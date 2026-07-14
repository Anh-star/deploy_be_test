package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.Payment;
import com.cmcu.itstudy.entity.SellerEarning;
import com.cmcu.itstudy.enums.PaymentStatus;
import com.cmcu.itstudy.enums.SellerEarningStatus;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.PaymentRepository;
import com.cmcu.itstudy.repository.SellerEarningRepository;
import com.cmcu.itstudy.service.contract.SellerBalanceService;
import com.cmcu.itstudy.service.contract.SellerEarningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class SellerEarningServiceImpl implements SellerEarningService {

    private static final Logger log = LoggerFactory.getLogger(SellerEarningServiceImpl.class);

    private final SellerEarningRepository sellerEarningRepository;
    private final PaymentRepository paymentRepository;
    private final DocumentRepository documentRepository;
    private final SellerBalanceService sellerBalanceService;

    @Value("${seller.platform-fee-percent:10}")
    private int platformFeePercent;

    @Value("${seller.earning-release-delay-hours:72}")
    private int releaseDelayHours;

    public SellerEarningServiceImpl(
            SellerEarningRepository sellerEarningRepository,
            PaymentRepository paymentRepository,
            DocumentRepository documentRepository,
            SellerBalanceService sellerBalanceService) {
        this.sellerEarningRepository = sellerEarningRepository;
        this.paymentRepository = paymentRepository;
        this.documentRepository = documentRepository;
        this.sellerBalanceService = sellerBalanceService;
    }

    @Override
    @Transactional
    public Optional<SellerEarning> recordSuccessfulPayment(UUID paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId must not be null");
        }

        Optional<SellerEarning> existing = sellerEarningRepository.findByPaymentId(paymentId);
        if (existing.isPresent()) {
            return existing;
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Payment is not SUCCESS: " + paymentId + ", status=" + payment.getStatus());
        }

        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            throw new IllegalStateException("Invalid payment amount: " + payment.getAmount());
        }

        if (platformFeePercent < 0 || platformFeePercent > 100) {
            throw new IllegalStateException("Invalid platform fee percent: " + platformFeePercent);
        }

        if (releaseDelayHours < 0) {
            throw new IllegalStateException("Invalid release delay hours: " + releaseDelayHours);
        }

        Optional<Document> documentOpt = documentRepository.findById(payment.getDocumentId());

        if (documentOpt.isEmpty()) {
            log.error(
                    "CRITICAL: Payment {} SUCCESS but Document {} not found. " +
                    "Manual reconciliation required. Buyer={}, Amount={}",
                    paymentId,
                    payment.getDocumentId(),
                    payment.getUserId(),
                    payment.getAmount());
            return Optional.empty();
        }

        Document document = documentOpt.get();

        if (document.getCreatedBy() == null || document.getCreatedBy().getId() == null) {
            throw new IllegalStateException("Document createdBy or createdBy.id is null: " + document.getId());
        }

        if (document.getTitle() == null || document.getTitle().isBlank()) {
            throw new IllegalStateException("Document title is null or blank: " + document.getId());
        }

        UUID sellerId = document.getCreatedBy().getId();
        UUID buyerId = payment.getUserId();
        UUID documentId = payment.getDocumentId();
        String titleSnapshot = document.getTitle();

        long grossAmount = payment.getAmount();
        long platformFee = (grossAmount * platformFeePercent) / 100;
        long sellerNetAmount = grossAmount - platformFee;

        if (sellerNetAmount < 0) {
            throw new IllegalStateException("Negative seller net amount: " + sellerNetAmount);
        }

        SellerEarning earning = SellerEarning.builder()
                .paymentId(paymentId)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .documentId(documentId)
                .documentTitleSnapshot(titleSnapshot)
                .grossAmount(grossAmount)
                .platformFee(platformFee)
                .sellerNetAmount(sellerNetAmount)
                .status(SellerEarningStatus.PENDING)
                .availableAt(java.time.LocalDateTime.now().plusHours(releaseDelayHours))
                .build();

        SellerEarning saved = sellerEarningRepository.save(earning);

        sellerBalanceService.creditPending(
                saved.getSellerId(),
                saved.getSellerNetAmount(),
                saved.getId()
        );

        return Optional.of(saved);
    }
}
