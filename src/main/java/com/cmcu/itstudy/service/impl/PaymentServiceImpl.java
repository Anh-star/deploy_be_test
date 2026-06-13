package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.VnPayConfig;
import com.cmcu.itstudy.dto.payment.CreatePaymentRequestDto;
import com.cmcu.itstudy.dto.payment.CreatePaymentResponseDto;
import com.cmcu.itstudy.dto.payment.PaymentHistoryDto;
import com.cmcu.itstudy.entity.Payment;
import com.cmcu.itstudy.enums.PaymentStatus;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.PaymentRepository;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.DocumentAccessService;
import com.cmcu.itstudy.service.contract.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final Long DEFAULT_AMOUNT = 50000L;

    private final PaymentRepository paymentRepository;
    private final DocumentRepository documentRepository;
    private final VnPayConfig vnPayConfig;
    private final DocumentAccessService documentAccessService;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                             DocumentRepository documentRepository,
                             VnPayConfig vnPayConfig,
                             DocumentAccessService documentAccessService) {
        this.paymentRepository = paymentRepository;
        this.documentRepository = documentRepository;
        this.vnPayConfig = vnPayConfig;
        this.documentAccessService = documentAccessService;
    }

    @Override
    @Transactional
    public CreatePaymentResponseDto createPayment(CreatePaymentRequestDto request, String ipAddress) {
        log.info("Creating payment... documentId={}", request.getDocumentId());

        UUID userId = getCurrentUserId();

        if (!documentRepository.existsById(request.getDocumentId())) {
            throw new NoSuchElementException("Document not found with id: " + request.getDocumentId());
        }

        String orderCode = vnPayConfig.generateOrderCode();
        log.info("Generated orderCode={} for user={}", orderCode, userId);

        Payment payment = Payment.builder()
                .userId(userId)
                .documentId(request.getDocumentId())
                .amount(DEFAULT_AMOUNT)
                .orderCode(orderCode)
                .status(PaymentStatus.PENDING)
                .paymentMethod("VNPay")
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        String paymentUrl = vnPayConfig.buildPaymentUrl(DEFAULT_AMOUNT, orderCode, ipAddress);
        log.info("Generated paymentUrl={} for paymentId={}", paymentUrl, savedPayment.getId());

        return CreatePaymentResponseDto.builder()
                .paymentId(savedPayment.getId())
                .paymentUrl(paymentUrl)
                .build();
    }

    @Override
    @Transactional
    public void processReturn(Map<String, String> params) {
        log.info("Return callback received: {}", params);

        if (!vnPayConfig.validateReturnChecksum(params)) {
            log.warn("Checksum invalid for return. params={}", params);
            throw new IllegalArgumentException("Invalid VNPay checksum");
        }

        String orderCode = params.get("vnp_TxnRef");
        log.info("Checksum valid for orderCode={}", orderCode);

        paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new NoSuchElementException("Payment not found for orderCode: " + orderCode));

        log.info("Payment found for return. orderCode={}", orderCode);
    }

    @Override
    @Transactional
    public void processIpn(Map<String, String> params) {
        log.info("IPN received: {}", params);

        if (!vnPayConfig.validateIpnChecksum(params)) {
            log.warn("Checksum invalid for IPN. params={}", params);
            return;
        }

        log.info("Checksum valid for IPN");

        String orderCode = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");

        log.info("Processing IPN: orderCode={}, responseCode={}, transactionNo={}, bankCode={}",
                orderCode, responseCode, transactionNo, bankCode);

        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElse(null);

        if (payment == null) {
            log.warn("Payment not found for IPN. orderCode={}", orderCode);
            return;
        }

        if (!"00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.FAILED);
            log.info("Payment FAILED: orderCode={}, responseCode={}", orderCode, responseCode);
        } else {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            log.info("Payment SUCCESS: orderCode={}, transactionNo={}", orderCode, transactionNo);
        }

        if (transactionNo != null && !transactionNo.isBlank()) {
            payment.setTransactionNo(transactionNo);
        }
        if (bankCode != null && !bankCode.isBlank()) {
            payment.setBankCode(bankCode);
        }

        paymentRepository.save(payment);
        log.info("Payment updated and saved: orderCode={}, status={}", orderCode, payment.getStatus());

        if (PaymentStatus.SUCCESS.equals(payment.getStatus())) {
            documentAccessService.grantAccess(
                payment.getUserId(),
                payment.getDocumentId(),
                payment.getId()
            );
            log.info("Document access granted for user={}, document={}, payment={}",
                    payment.getUserId(), payment.getDocumentId(), payment.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentHistoryDto> getMyPaymentHistory() {
        UUID userId = getCurrentUserId();
        List<Payment> payments = paymentRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        return payments.stream()
                .map(this::toPaymentHistoryDto)
                .collect(Collectors.toList());
    }

    private PaymentHistoryDto toPaymentHistoryDto(Payment payment) {
        return PaymentHistoryDto.builder()
                .documentId(payment.getDocumentId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .bankCode(payment.getBankCode())
                .transactionNo(payment.getTransactionNo())
                .createdAt(payment.getCreatedAt())
                .paidAt(payment.getPaidAt())
                .build();
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return userDetails.getUser().getId();
    }
    
}
