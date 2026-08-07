package com.cmcu.itstudy.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCardResponseDto {
    private String id;
    private String title;
    private String thumbnail;
    private String categoryName;
    /** @deprecated Prefer {@link #uploader} / {@link #userId}; kept for API compatibility (same value as uploader full name). */
    private String authorName;
    /** Uploader user id (document.created_by). */
    private String userId;
    private DocumentUploaderDto uploader;
    private LocalDateTime createdAt;
    private Integer viewCount;
    private Integer downloadCount;
    private Boolean isBookmarked;
    private FileTypeDto fileType;
    private List<String> tags;
    private LocalDateTime lastViewedAt;
}
