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
import com.cmcu.itstudy.enums.NotificationType;
import com.cmcu.itstudy.enums.PaymentStatus;
import com.cmcu.itstudy.repository.DocumentAccessRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.PaymentRepository;
import com.cmcu.itstudy.security.UserDetailsImpl;
import com.cmcu.itstudy.service.contract.DocumentAccessService;
import com.cmcu.itstudy.service.contract.NotificationService;
import com.cmcu.itstudy.service.contract.PayOsService;
import com.cmcu.itstudy.service.contract.PaymentService;
import com.cmcu.itstudy.service.contract.SellerEarningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<VnPayConfig> vnPayConfigProvider;
    private final DocumentAccessService documentAccessService;
    private final PayOsService payOsService;
    private final SellerEarningService sellerEarningService;
    private final NotificationService notificationService;
    private final com.cmcu.itstudy.repository.UserRepository userRepository;
    private final com.cmcu.itstudy.repository.NotificationRepository notificationRepository;
    private final com.cmcu.itstudy.service.impl.SseService sseService;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                             DocumentRepository documentRepository,
                             DocumentAccessRepository documentAccessRepository,
                             ObjectProvider<VnPayConfig> vnPayConfigProvider,
                             DocumentAccessService documentAccessService,
                             PayOsService payOsService,
                             SellerEarningService sellerEarningService,
                             NotificationService notificationService,
                             com.cmcu.itstudy.repository.UserRepository userRepository,
                             com.cmcu.itstudy.repository.NotificationRepository notificationRepository,
                             com.cmcu.itstudy.service.impl.SseService sseService) {
        this.paymentRepository = paymentRepository;
        this.documentRepository = documentRepository;
        this.documentAccessRepository = documentAccessRepository;
        this.vnPayConfigProvider = vnPayConfigProvider;
        this.documentAccessService = documentAccessService;
        this.payOsService = payOsService;
        this.sellerEarningService = sellerEarningService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.sseService = sseService;
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

    VnPayConfig vnPayConfig = requireVnPayConfig();

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

    if (payment.getStatus() == PaymentStatus.SUCCESS) {
        ensureSuccessfulPaymentSideEffects(payment);
        return;
    }

    if ("00".equals(responseCode)) {

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionNo(transactionNo);
        payment.setBankCode(bankCode);

        paymentRepository.save(payment);

        ensureSuccessfulPaymentSideEffects(payment);

    } else {

        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);
    }
}

    @Override
    @Transactional
    public void processIpn(Map<String, String> params) {
        log.info("IPN received: {}", params);

        VnPayConfig vnPayConfig = requireVnPayConfig();

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

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            ensureSuccessfulPaymentSideEffects(payment);
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
            ensureSuccessfulPaymentSideEffects(payment);
        }
    }

    @Override
    @Transactional
    public List<PaymentHistoryDto> getMyPaymentHistory() {
        // Auto-cancel any pending payments older than 15 minutes before fetching
        cancelExpiredPendingPayments(15);

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
    public int cancelExpiredPendingPayments(int expirationMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(expirationMinutes);
        int count = paymentRepository.cancelExpiredPendingPayments(threshold);
        if (count > 0) {
            log.info("Auto-cancelled {} expired pending payments (threshold: older than {} minutes)", count, expirationMinutes);
        }
        return count;
    }

    @Override
    @Transactional
    public void processPayOsWebhook(PayOsWebhookDto payload) {
        String orderCode = String.valueOf(payload.getData().getOrderCode());
        log.info("Processing PayOS webhook: orderCode={}", orderCode);

        Payment payment = paymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new NoSuchElementException("Payment not found with orderCode: " + orderCode));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.info("PayOS webhook: payment already SUCCESS, reconciling side effects: orderCode={}", orderCode);
            ensureSuccessfulPaymentSideEffects(payment);
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
            payment.setStatus(PaymentStatus.CANCELLED);
            log.info("PayOS webhook: payment CANCELLED updated: orderCode={}", orderCode);
        }

        paymentRepository.save(payment);

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            ensureSuccessfulPaymentSideEffects(payment);
        }
    }

    /**
     * Resolves the VNPayConfig bean, throwing if it is not available.
     * VNPay is disabled on Render so this bean is absent — callers must
     * call this method only from the VNPay-specific return/IPN handlers.
     */
    private VnPayConfig requireVnPayConfig() {
        VnPayConfig config = vnPayConfigProvider.getIfAvailable();
        if (config == null) {
            throw new IllegalStateException("VNPay is disabled");
        }
        return config;
    }

    /**
     * Idempotent post-SUCCESS side effects. Runs in the caller's transaction so
     * payment/access/earning succeed or fail together. Each downstream call has
     * its own idempotency guard (early-return on existing rows + DB UNIQUE),
     * so safe to invoke on every SUCCESS transition including retries for
     * legacy payments that completed before this wiring existed.
     */
    private void ensureSuccessfulPaymentSideEffects(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("payment must not be null");
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Side effects require SUCCESS status, got: " + payment.getStatus());
        }
        UUID userId = payment.getUserId();
        UUID documentId = payment.getDocumentId();
        UUID paymentId = payment.getId();
        if (userId == null || documentId == null || paymentId == null) {
            throw new IllegalStateException(
                    "Payment is missing required ids: userId/documentId/paymentId");
        }

        documentAccessService.grantAccess(userId, documentId, paymentId);

        sellerEarningService.recordSuccessfulPayment(paymentId);

        try {
            documentRepository.findByIdAndDeletedFalse(documentId)
                    .or(() -> documentRepository.findById(documentId))
                    .ifPresent(document -> {
                if (document.getCreatedBy() != null && document.getCreatedBy().getId() != null) {
                    sendAggregatedDocumentPurchaseNotification(document.getCreatedBy().getId(), userId, document);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to push DOCUMENT_PURCHASED notification for paymentId={}: {}", paymentId, e.getMessage());
        }

        log.info(
                "Payment SUCCESS side effects applied: paymentId={}, userId={}, documentId={}",
                paymentId, userId, documentId
        );
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

    private void sendAggregatedDocumentPurchaseNotification(UUID ownerId, UUID buyerUserId, Document document) {
        if (ownerId == null || buyerUserId == null || ownerId.equals(buyerUserId)) return;
        UUID docId = document.getId();
        String docTitle = (document.getTitle() != null && !document.getTitle().isBlank()) ? document.getTitle() : "tài liệu";
        User buyer = userRepository.findById(buyerUserId).orElse(null);
        String singleBuyerName = (buyer != null && buyer.getFullName() != null && !buyer.getFullName().isBlank())
                ? buyer.getFullName() : "Một người dùng";
        String singleMsg = singleBuyerName + " đã mua tài liệu \"" + docTitle + "\" của bạn.";

        try {
            List<com.cmcu.itstudy.entity.Notification> existingList =
                    notificationRepository.findAllDocumentPurchaseNotifications(ownerId, docId.toString());

            if (existingList.isEmpty()) {
                notificationService.createAndPush(
                        ownerId,
                        buyerUserId,
                        NotificationType.DOCUMENT_PURCHASED,
                        docId.toString(),
                        "DOCUMENT",
                        singleMsg
                );
                return;
            }

            // Aggregate with existing notification
            com.cmcu.itstudy.entity.Notification existing = existingList.get(0);
            List<String> buyerNames = documentAccessRepository.findDistinctBuyerNamesByDocumentOrderedByRecent(docId);

            String aggregatedMessage;
            if (buyerNames == null || buyerNames.size() <= 1) {
                aggregatedMessage = singleMsg;
            } else if (buyerNames.size() == 2) {
                String first = buyerNames.get(0);
                String second = buyerNames.get(1);
                aggregatedMessage = first + " và " + second + " đã mua tài liệu \"" + docTitle + "\" của bạn.";
            } else {
                String first = buyerNames.get(0);
                int othersCount = buyerNames.size() - 1;
                aggregatedMessage = first + " và " + othersCount + " người khác đã mua tài liệu \"" + docTitle + "\" của bạn.";
            }

            existing.setMessage(aggregatedMessage);
            if (buyer != null) {
                existing.setActor(buyer);
            }
            existing.setCreatedAt(java.time.LocalDateTime.now());
            existing.setRead(false);
            com.cmcu.itstudy.entity.Notification saved = notificationRepository.save(existing);

            // Clean up any extra duplicates
            if (existingList.size() > 1) {
                for (int i = 1; i < existingList.size(); i++) {
                    com.cmcu.itstudy.entity.Notification dup = existingList.get(i);
                    notificationRepository.delete(dup);
                    try {
                        java.util.Map<String, Object> removeData = new java.util.HashMap<>();
                        removeData.put("id", dup.getId().toString());
                        removeData.put("action", "DELETE");
                        sseService.pushEvent(ownerId, "notification-removed", removeData);
                    } catch (Exception ignored) {}
                }
            }

            // Push updated SSE notification
            try {
                com.cmcu.itstudy.dto.notification.NotificationResponseDto dto = com.cmcu.itstudy.dto.notification.NotificationResponseDto.builder()
                        .id(saved.getId().toString())
                        .actorId(buyer != null ? buyer.getId().toString() : null)
                        .actorName(singleBuyerName)
                        .actorAvatar(buyer != null ? buyer.getAvatarUrl() : null)
                        .type(saved.getType())
                        .referenceId(saved.getReferenceId())
                        .referenceType(saved.getReferenceType())
                        .message(saved.getMessage())
                        .isRead(false)
                        .createdAt(saved.getCreatedAt())
                        .build();
                sseService.pushEvent(ownerId, "notification", dto);
            } catch (Exception sseEx) {
                log.warn("Failed to push aggregated purchase SSE notification to user {}: {}", ownerId, sseEx.getMessage());
            }

        } catch (Exception ex) {
            log.warn("Failed to send aggregated purchase notification: {}", ex.getMessage());
        }
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl userDetails)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return userDetails.getUser().getId();
    }

}
