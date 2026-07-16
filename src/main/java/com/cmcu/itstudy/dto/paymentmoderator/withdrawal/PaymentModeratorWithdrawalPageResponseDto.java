package com.cmcu.itstudy.dto.paymentmoderator.withdrawal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentModeratorWithdrawalPageResponseDto {

    private List<PaymentModeratorWithdrawalListItemDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
