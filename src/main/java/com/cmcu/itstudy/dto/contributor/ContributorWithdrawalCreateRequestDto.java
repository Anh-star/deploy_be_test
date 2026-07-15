package com.cmcu.itstudy.dto.contributor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ContributorWithdrawalCreateRequestDto {

    @NotNull(message = "Amount is required")
    @Min(
            value = 5001,
            message = "Withdrawal amount must be at least 5001"
    )
    @Max(
            value = 999999,
            message = "Withdrawal amount must not exceed 999999"
    )
    private Long amount;

    @NotNull(message = "Client request ID is required")
    private UUID clientRequestId;

    @Size(
            max = 1000,
            message = "Seller note must not exceed 1000 characters"
    )
    private String sellerNote;
}
