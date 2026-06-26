package com.cmcu.itstudy.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentResponseDto {

    private UUID paymentId;

    private String orderCode;

    private String checkoutUrl;

    private String qrCode;

    private Long amount;

    /**
     * Kept as alias of checkoutUrl so legacy FE code that reads paymentUrl
     * still works during migration. Prefer checkoutUrl in new code.
     */
    private String paymentUrl;
}