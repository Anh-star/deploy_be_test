package com.cmcu.itstudy.dto.document;

import com.cmcu.itstudy.enums.DocumentStatus;
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
public class MyDocumentDetailDto {

    private String id;
    private String title;
    private String description;
    private String documentUrl;
    private String thumbnailUrl;
    private String fileName;
    private String fileType;
    private Long fileSizeBytes;
    private String categoryName;
    private List<String> tags;
    private DocumentStatus status;
    private String rejectReason;
    private LocalDateTime createdAt;

    /**
     * Whether the document is monetised. Sourced directly from
     * {@code Document#isPaid} via {@code DocumentServiceImpl#getMyDocumentDetail}.
     * Pricing-lock / purchase-count fields are intentionally omitted in this
     * sub-phase and will be added once the SUCCESS payment query exists.
     */
    private Boolean isPaid;

    /**
     * Integer VND price the buyer pays. {@code 0L} (or {@code null}) for free
     * documents. Sourced directly from {@code Document#price}.
     */
    private Long price;

    /**
     * True iff at least one non-owner buyer has successfully paid for this
     * document. Sourced from
     * {@code PaymentRepository#countByDocumentIdAndStatusAndUserIdNot}
     * (status = {@code PaymentStatus.SUCCESS}, userId != owner).
     *
     * <p>When true, contributor edits to {@link #isPaid} / {@link #price}
     * must be rejected with HTTP 409 by the update service. Metadata-only
     * updates (title / description / category / tags / file / thumbnail)
     * remain allowed.
     */
    private Boolean pricingLocked;

    /**
     * Number of successful non-owner payments on this document. Used to
     * surface "Số lượt mua thành công" on owner detail / submitted detail.
     *
     * <p>Counted by payment row, not by distinct buyer. If the business
     * later needs distinct-buyer semantics, add a dedicated
     * repository method rather than mutating this contract.
     */
    private Long successfulPurchaseCount;
}
