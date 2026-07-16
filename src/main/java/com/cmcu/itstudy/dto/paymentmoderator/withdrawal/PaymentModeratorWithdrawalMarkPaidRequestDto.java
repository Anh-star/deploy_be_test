package com.cmcu.itstudy.dto.paymentmoderator.withdrawal;

import jakarta.validation.constraints.NotBlank;
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
public class PaymentModeratorWithdrawalMarkPaidRequestDto {

    @NotBlank(
            message = "Payment confirmation note is required"
    )
    @Size(
            max = 1000,
            message = "Payment confirmation note must not exceed 1000 characters"
    )
    private String adminNote;
}