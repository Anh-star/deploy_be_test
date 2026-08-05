package com.cmcu.itstudy.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReportResponseDto {

    private String id;
    private String documentId;
    private String documentTitle;
    private String documentAuthorId;
    private String documentAuthorName;
    private String documentAuthorAvatar;
    private String reporterId;
    private String reporterName;
    private String reporterAvatar;
    private String reasonCode;
    private String detail;
    private String status;
    private Long reportCount;
    private String documentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
