package com.cmcu.itstudy.dto.document;

import jakarta.validation.constraints.NotBlank;
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
public class DocumentReportRequestDto {

    @NotBlank(message = "Reason code is required")
    private String reasonCode;

    private String detail;
}
