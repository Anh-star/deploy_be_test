package com.cmcu.itstudy.dto.contributor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateDto {
    @NotBlank(message = "URL chứng chỉ không được để trống")
    @Pattern(regexp = "^https?://.*", message = "URL chứng chỉ không hợp lệ")
    private String url;

    @NotBlank(message = "Tên chứng chỉ không được để trống")
    private String certificateName;
}
