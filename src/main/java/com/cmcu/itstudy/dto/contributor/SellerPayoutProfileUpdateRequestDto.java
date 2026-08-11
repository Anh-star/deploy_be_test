package com.cmcu.itstudy.dto.contributor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class SellerPayoutProfileUpdateRequestDto {

    @NotBlank(message = "Bank code is required")
    @Size(max = 32, message = "Bank code must not exceed 32 characters")
    private String bankCode;

    @NotBlank(message = "Bank name is required")
    @Size(max = 255, message = "Bank name must not exceed 255 characters")
    private String bankName;

    @NotBlank(message = "Bank account number is required")
    @Pattern(
            regexp = "^[0-9]{7,19}$",
            message = "Bank account number must contain 7 to 19 digits"
    )
    @Size(
            max = 19,
            message = "Bank account number must not exceed 19 characters"
    )
    private String bankAccountNumber;

    @NotBlank(message = "Bank account holder name is required")
    @Size(
            max = 255,
            message = "Bank account holder name must not exceed 255 characters"
    )
    private String bankAccountHolderName;
}