package com.cmcu.itstudy.dto.paymentmoderator.withdrawal;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentModeratorWithdrawalApproveRequestDto {

    @Size(
            max = 1000,
            message = "Admin note must not exceed 1000 characters"
    )
    private String adminNote;
}