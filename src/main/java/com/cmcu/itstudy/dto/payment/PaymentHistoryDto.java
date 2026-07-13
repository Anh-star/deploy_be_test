package com.cmcu.itstudy.dto.payment;

import com.cmcu.itstudy.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryDto {

    private UUID paymentId;

    private UUID documentId;

    private String documentTitle;

    private Long amount;

    private PaymentStatus status;

    private String orderCode;

    private String bankCode;

    private String transactionNo;

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;
}
