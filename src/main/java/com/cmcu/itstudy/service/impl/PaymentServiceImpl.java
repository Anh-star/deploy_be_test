package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.VnPayConfig;
import com.cmcu.itstudy.dto.payment.CreatePaymentRequestDto;
import com.cmcu.itstudy.dto.payment.CreatePaymentResponseDto;
import com.cmcu.itstudy.dto.payment.PayOsCreateLinkResponseDto;
import com.cmcu.itstudy.dto.payment.PaymentHistoryDto;
import com.cmcu.itstudy.dto.payment.PayOsWebhookDto;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.Payment;
import com.cmcu.itstudy.enums.DocumentStatus;
import com.cmcu.itstudy.enums.PaymentStatus;
import com.cmcu.itstudy.repository.DocumentAccessRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.PaymentRepository;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.DocumentAccessService;
import com.cmcu.itstudy.service.contract.PayOsService;
import com.cmcu.itstudy.service.contract.PaymentService;
import com.cmcu.itstudy.service.contract.SellerEarningService;
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

    private final PaymentRepository paymentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAccessRepository documentAccessRepository;
    private final VnPayConfig vnPayConfig;
    private final DocumentAccessService documentAccessService;
    private final PayOsService payOsService;
    private final SellerEarningService sellerEarningService;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                             DocumentRepository documentRepository,
                             DocumentAccessRepository documentAccessRepository,
                             VnPayConfig vnPayConfig,
                             DocumentAccessService documentAccessService,
                             PayOsService payOsService,
                             SellerEarningService sellerEarningService) {
        this.paymentRepository = paymentRepository;
        this.documentRepository = documentRepository;
        this.documentAccessRepository = documentAccessRepository;
        this.vnPayConfig = vnPayConfig;
        this.documentAccessService = documentAccessService;
        this.payOsService = payOsService;
        this.sellerEarningService = sellerEarningService;
    }

    @Override
    @Transactional
    public CreatePaymentResponseDto createPayment(CreatePaymentRequestDto request, String ipAddress) {
        log.info("Creating payment... documentId={}", request.getDocumentId());

        UUID userId = getCurrentUserId();

        Document document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new NoSuchElementException("Document not found with id: " + request.getDocumentId()));

        if (!Boolean.TRUE.equals(document.getIsPaid())) {
            throw new IllegalStateException("Document is not a paid document");
        }
        if (document.getPrice() == null || document.getPrice() <= 0L) {
            throw new IllegalStateException("Document price must be greater than zero");
        }
        if (document.getStatus() != DocumentStatus.APPROVED) {
            throw new IllegalStateException("Document is not approved for sale");
        }
        if (document.getCreatedBy() != null && document.getCreatedBy().getId().equals(userId)) {
            throw new IllegalStateException("You cannot purchase your own document");
        }
        if (documentAccessRepository.existsByUserIdAndDocumentId(userId, request.getDocumentId())) {
            throw new IllegalStateException("You already have access to this document");
        }

        long amount = document.getPrice();
        long orderCodeLong = System.currentTimeMillis();
        String orderCode = String.valueOf(orderCodeLong);
        String description = "TT" + orderCode.substring(Math.max(0, orderCode.length() - 7));
        log.info("Generated orderCode={} description={} for user={} amount={}", orderCode, description, userId, amount);

        Payment payment = Payment.builder()
                .userId(userId)
                .documentId(request.getDocumentId())
                .amount(amount)
                .orderCode(orderCode)
                .status(PaymentStatus.PENDING)
                .paymentMethod("PayOS")
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        PayOsCreateLinkResponseDto payosResponse = payOsService.createPaymentLink(
                orderCodeLong,
                amount,
                description
        );
        String checkoutUrl = payosResponse.getData() != null ? payosResponse.getData().getCheckoutUrl() : null;
        String qrCode = payosResponse.getData() != null ? payosResponse.getData().getQrCode() : null;
        log.info("Generated PayOS checkoutUrl for paymentId={} user={}", savedPayment.getId(), userId);

        return CreatePaymentResponseDto.builder()
                .paymentId(savedPayment.getId())
                .orderCode(orderCode)
                .checkoutUrl(checkoutUrl)
                .qrCode(qrCode)
                .amount(amount)
                .paymentUrl(checkoutUrl)
                .build();
    }

    @Override
@Transactional
public void processReturn(Map<String, String> params) {

    if (!vnPayConfig.validateReturnChecksum(params)) {
        throw new IllegalArgumentException("Invalid checksum");
    }

    String orderCode = params.get("vnp_TxnRef");
    String responseCode = params.get("vnp_ResponseCode");
    String transactionNo = params.get("vnp_TransactionNo");
    String bankCode = params.get("vnp_BankCode");

    Payment payment = paymentRepository.findByOrderCode(orderCode)
            .orElseThrow(() ->
                    new NoSuchElementException("Payment not found"));

    if ("00".equals(responseCode)) {

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionNo(transactionNo);
        payment.setBankCode(bankCode);

        paymentRepository.save(payment);

        documentAccessService.grantAccess(
                payment.getUserId(),
                payment.getDocumentId(),
                payment.getId()
        );

    } else {

        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);
    }
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

        if (payments.isEmpty()) {
            return List.of();
        }

        List<UUID> documentIds = payments.stream()
                .map(Payment::getDocumentId)
                .distinct()
                .toList();

        Map<UUID, String> titleByDocumentId = documentRepository.findAllById(documentIds).stream()
                .collect(Collectors.toMap(Document::getId, Document::getTitle));

        return payments.stream()
                .map(payment -> toPaymentHistoryDto(payment, titleByDocumentId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void processPayOsWebhook(PayOsWebhookDto payload) {
        String orderCode = String.valueOf(payload.getData().getOrderCode());
        log.info("Processing PayOS webhook: orderCode={}", orderCode);

        Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new NoSuchElementException("Payment not found with orderCode: " + orderCode));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.info("PayOS webhook ignored - payment already SUCCESS: orderCode={}", orderCode);
            return;
        }

        if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.CANCELLED) {
            log.info("PayOS webhook ignored - payment already in terminal status {}: orderCode={}",
                    payment.getStatus(), orderCode);
            return;
        }

        if (payOsService.isSuccessPayload(payload)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            if (payload.getData().getReference() != null && !payload.getData().getReference().isBlank()) {
                payment.setTransactionNo(payload.getData().getReference());
            }
            log.info("PayOS webhook: payment SUCCESS updated: orderCode={}", orderCode);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            log.info("PayOS webhook: payment FAILED updated: orderCode={}", orderCode);
        }

        paymentRepository.save(payment);

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            documentAccessService.grantAccess(
                    payment.getUserId(),
                    payment.getDocumentId(),
                    payment.getId()
            );
            log.info("PayOS webhook: document access granted: orderCode={}, userId={}, documentId={}",
                    orderCode, payment.getUserId(), payment.getDocumentId());

            try {
                var earning = sellerEarningService.recordSuccessfulPayment(payment.getId());
                if (earning.isEmpty()) {
                    log.warn("PayOS webhook: seller earning not recorded (Document missing or reconciliation required). paymentId={}",
                            payment.getId());
                } else {
                    log.info("PayOS webhook: seller earning recorded. paymentId={}, earningId={}",
                            payment.getId(), earning.get().getId());
                }
            } catch (RuntimeException ex) {
                log.warn("PayOS webhook: seller earning service failed. paymentId={}, reason={}",
                        payment.getId(), ex.getMessage());
                throw ex;
            }
        }
    }

    private PaymentHistoryDto toPaymentHistoryDto(Payment payment, Map<UUID, String> titleByDocumentId) {
        return PaymentHistoryDto.builder()
                .paymentId(payment.getId())
                .documentId(payment.getDocumentId())
                .documentTitle(titleByDocumentId.get(payment.getDocumentId()))
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .orderCode(payment.getOrderCode())
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
