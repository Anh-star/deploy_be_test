package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.payment.PayOsCreateLinkResponseDto;
import com.cmcu.itstudy.dto.payment.PayOsWebhookDto;

public interface PayOsService {

    PayOsCreateLinkResponseDto createPaymentLink(long orderCode, long amount, String description);

    boolean verifyWebhookSignature(PayOsWebhookDto payload);

    boolean isSuccessPayload(PayOsWebhookDto payload);
}