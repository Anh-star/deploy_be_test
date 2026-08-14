package com.cmcu.itstudy.dto.admin.document;

import com.cmcu.itstudy.enums.DocumentStatus;
import jakarta.validation.constraints.NotNull;
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
public class DocumentAdminStatusPatchRequestDto {

    @NotNull(message = "status is required")
    private DocumentStatus status;

    /** Required when status is REJECTED */
    private String rejectReason;

    /** Optional moderator note */
    private String adminNote;
}
