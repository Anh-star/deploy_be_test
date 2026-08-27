package com.cmcu.itstudy.dto.document;

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
public class DocumentReportPageResponseDto {

    private List<DocumentReportResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private long pendingCount;
    private long resolvedCount;
    private long dismissedCount;
}
