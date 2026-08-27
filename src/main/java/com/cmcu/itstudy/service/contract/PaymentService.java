package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.payment.CreatePaymentRequestDto;
import com.cmcu.itstudy.dto.payment.CreatePaymentResponseDto;
import com.cmcu.itstudy.dto.payment.PaymentHistoryDto;
import com.cmcu.itstudy.dto.payment.PayOsWebhookDto;

import java.util.List;
import java.util.Map;

public interface PaymentService {

    CreatePaymentResponseDto createPayment(CreatePaymentRequestDto request, String ipAddress);

    void processReturn(Map<String, String> params);

    void processIpn(Map<String, String> params);

    List<PaymentHistoryDto> getMyPaymentHistory();

    void processPayOsWebhook(PayOsWebhookDto payload);

    int cancelExpiredPendingPayments(int expirationMinutes);
}
