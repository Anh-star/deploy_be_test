package com.cmcu.itstudy.dto.document;

import com.cmcu.itstudy.enums.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCardDto {

    private String id;
    private String title;
    private String slug;
    private String description;
    private String thumbnailUrl;
    private String fileName;
    private String fileType; // Use enum name as String
    private Long fileSize;
    private DocumentStatus status;
    private LocalDateTime uploadDate;
    private Long views;
    private Long downloads;
    private Long bookmarks;

    // Added fields to match frontend expectations and service implementation
    private String categoryName;
    private String authorName;
    private List<String> tags;
    private String documentUrl;
    /** Primary file storage path (Supabase object key) when a DocumentFile row exists. */
    private String storagePath;

/**
 * Whether the document is monetised. Sourced directly from
 * {@code Document#isPaid} via the list mapping. Pricing-lock flag and
 * successful purchase count are intentionally omitted in this sub-phase
 * to avoid N+1 queries against {@code PaymentRepository}.
 */
private Boolean isPaid;

/**
 * Integer VND price the buyer pays. {@code 0L} for free documents.
 * Sourced directly from {@code Document#price}.
 */
private Long price;

/**
 * True iff at least one non-owner buyer has successfully paid for this
 * document. Sourced from a single bulk query against
 * {@code PaymentRepository#findDistinctDocumentIdsWithSuccessfulBuyer}
 * for the whole page, then per-card membership check.
 */
private Boolean pricingLocked;
}
