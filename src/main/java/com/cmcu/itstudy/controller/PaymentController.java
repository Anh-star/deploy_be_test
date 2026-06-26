package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.payment.CreatePaymentRequestDto;
import com.cmcu.itstudy.dto.payment.CreatePaymentResponseDto;
import com.cmcu.itstudy.dto.payment.PaymentHistoryDto;
import com.cmcu.itstudy.service.contract.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
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
