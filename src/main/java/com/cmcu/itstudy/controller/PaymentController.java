package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.payment.CreatePaymentRequestDto;
import com.cmcu.itstudy.dto.payment.CreatePaymentResponseDto;
import com.cmcu.itstudy.dto.payment.PaymentHistoryDto;
import com.cmcu.itstudy.dto.payment.PayOsWebhookDto;
import com.cmcu.itstudy.service.contract.PaymentService;
import com.cmcu.itstudy.service.contract.PayOsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final PayOsService payOsService;

    public PaymentController(PaymentService paymentService, PayOsService payOsService) {
        this.paymentService = paymentService;
        this.payOsService = payOsService;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<CreatePaymentResponseDto>> createPayment(
            @Valid @RequestBody CreatePaymentRequestDto request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        CreatePaymentResponseDto response = paymentService.createPayment(request, ipAddress);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment URL generated"));
    }

    @GetMapping("/return")
    public ResponseEntity<ApiResponse<Void>> paymentReturn(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        paymentService.processReturn(params);

        String responseCode = params.get("vnp_ResponseCode");
        if ("00".equals(responseCode)) {
            return ResponseEntity.ok(ApiResponse.success(null, "Payment successful"));
        } else {
            return ResponseEntity.ok(ApiResponse.failure("Payment failed with response code: " + responseCode));
        }
    }

    @GetMapping("/ipn")
    public ResponseEntity<String> paymentIpn(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        paymentService.processIpn(params);

        String responseCode = params.get("vnp_ResponseCode");
        if ("00".equals(responseCode)) {
            return ResponseEntity.ok("{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}");
        } else {
            return ResponseEntity.ok("{\"RspCode\":\"\",\"Message\":\"Confirm Success\"}");
        }
    }

    @GetMapping("/my-history")
    public ResponseEntity<ApiResponse<List<PaymentHistoryDto>>> getMyHistory() {
        List<PaymentHistoryDto> history = paymentService.getMyPaymentHistory();
        return ResponseEntity.ok(ApiResponse.success(history, "Payment history retrieved"));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> payOsWebhook(@RequestBody PayOsWebhookDto payload, HttpServletRequest request) {
        log.info("PayOS webhook request: method={}, uri={}, contentType={}, contentLength={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getContentType(),
                request.getContentLengthLong());
        log.info("PayOS webhook headers: User-Agent={}, Content-Length={}, Content-Type={}",
                request.getHeader("User-Agent"),
                request.getHeader("Content-Length"),
                request.getHeader("Content-Type"));
        boolean payloadIsNull = (payload == null);
        boolean dataIsNull = payloadIsNull || (payload.getData() == null);
        boolean signatureIsNull = payloadIsNull || (payload.getSignature() == null);
        log.info("PayOS webhook payload nullability: payloadNull={}, dataNull={}, signatureNull={}",
                payloadIsNull, dataIsNull, signatureIsNull);

        if (payload == null || payload.getData() == null || payload.getSignature() == null) {
            log.warn("PayOS webhook received malformed payload: payload={}", payload);
            return ResponseEntity.badRequest().body(Map.of(
                    "received", false,
                    "signatureValid", false,
                    "error", "malformed_payload"
            ));
        }

        log.info("PayOS webhook received: orderCode={}, code={}, success={}",
                payload.getData().getOrderCode(),
                payload.getCode(),
                payload.getSuccess());

        boolean valid = payOsService.verifyWebhookSignature(payload);

        if (!valid) {
            log.warn("PayOS webhook signature invalid: orderCode={}",
                    payload.getData().getOrderCode());
            return ResponseEntity.badRequest().body(Map.of(
                    "received", false,
                    "signatureValid", false,
                    "error", "invalid_signature"
            ));
        }

        try {
            paymentService.processPayOsWebhook(payload);
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "signatureValid", true
            ));
        } catch (NoSuchElementException e) {
            log.warn("PayOS webhook payment not found: orderCode={}",
                    payload.getData().getOrderCode());
            return ResponseEntity.badRequest().body(Map.of(
                    "received", false,
                    "signatureValid", true,
                    "error", "payment_not_found"
            ));
        }
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            String[] values = e.getValue();
                            return values != null && values.length > 0 ? values[0] : "";
                        }
                ));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
