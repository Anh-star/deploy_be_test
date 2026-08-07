package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.document.PreviewLockedReason;
import com.cmcu.itstudy.handle.PaidPreviewUnavailableException;

/**
 * Pure-function calculator for the visible-page count of a paid
 * preview PDF derivative.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@code totalPages >= 6} → {@code 5}</li>
 *   <li>{@code 2 <= totalPages <= 5} → {@code totalPages - 1}</li>
 *   <li>{@code totalPages == 1} → throws
 *       {@link PaidPreviewUnavailableException} carrying
 *       {@link PreviewLockedReason#ONE_PAGE_PAID_DOCUMENT}</li>
 *   <li>{@code totalPages <= 0} → throws
 *       {@link PaidPreviewUnavailableException} carrying
 *       {@link PreviewLockedReason#PREVIEW_UNAVAILABLE}</li>
 * </ul>
 *
 * <p>The calculator is intentionally stateless: every refusal
 * propagates the cause through a typed exception so that the caller
 * can read the {@link PreviewLockedReason} from the exception itself
 * without consulting any mutable singleton field.</p>
 */
public interface PaidPdfPageRuleService {

    /**
     * Compute the visible-page count for a paid preview PDF derivative.
     *
     * @param totalPages total page count of the original PDF
     * @return visible page count (always {@code >= 0})
     * @throws PaidPreviewUnavailableException if the page count
     *         indicates the PDF cannot produce a safe derivative (1
     *         page, 0 pages, negative count). The exception carries
     *         the {@link PreviewLockedReason} that explains the cause.
     */
    int calculateLimitedPreviewPageCount(int totalPages);
}