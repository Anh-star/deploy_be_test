package com.cmcu.itstudy.dto.admin.document;

import com.cmcu.itstudy.enums.DocumentStatus;
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
public class DocumentAdminDetailDto {

    private String id;
    private String title;
    private String description;
    /** Public URL dùng để preview / mở tab (Supabase hoặc URL đầy đủ khác). */
    private String fileUrl;
    private String thumbnailUrl;
    private String fileType;
    private String fileName;
    private Long fileSizeBytes;
    private String authorName;
    private String categoryName;
    private DocumentStatus status;
    private LocalDateTime createdAt;
    private String rejectReason;
    /** Object key hoặc path lưu trữ (DocumentFile), có thể null. */
    private String storagePath;
    private Boolean isPaid;
    private Long price;
}
