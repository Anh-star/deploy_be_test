package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.document.PreviewLockedReason;
import com.cmcu.itstudy.handle.PaidPreviewUnavailableException;
import com.cmcu.itstudy.service.contract.PaidPdfPageRuleService;
import org.springframework.stereotype.Service;

/**
 * Stateless implementation of {@link PaidPdfPageRuleService}.
 *
 * <p>All refusal paths throw {@link PaidPreviewUnavailableException}
 * with the locked reason embedded as a final field on the exception.
 * Callers MUST read the reason from the exception; this implementation
 * deliberately exposes NO mutable singleton state (no
 * {@code lastLockedReason} field, no ThreadLocal).</p>
 */
@Service
public class PaidPdfPageRuleServiceImpl implements PaidPdfPageRuleService {

    private static final int LOCKED_MAX_PAGE_COUNT = 1;

    private static final int FULL_PREVIEW_MAX_PAGE_COUNT = 5;

    @Override
    public int calculateLimitedPreviewPageCount(int totalPages) {
        if (totalPages > FULL_PREVIEW_MAX_PAGE_COUNT) {
            // totalPages >= 6
            return FULL_PREVIEW_MAX_PAGE_COUNT;
        }
        if (totalPages == FULL_PREVIEW_MAX_PAGE_COUNT) {
            // totalPages == 5
            return FULL_PREVIEW_MAX_PAGE_COUNT - 1;
        }
        if (totalPages > LOCKED_MAX_PAGE_COUNT) {
            // 2..4
            return totalPages - 1;
        }
        if (totalPages == LOCKED_MAX_PAGE_COUNT) {
            // Exactly one page — exposing any portion of it would expose
            // the entire paid document. Refuse the derivative.
            throw new PaidPreviewUnavailableException(
                    PreviewLockedReason.ONE_PAGE_PAID_DOCUMENT,
                    "Single-page paid document cannot produce a limited preview");
        }
        // totalPages <= 0
        throw new PaidPreviewUnavailableException(
                PreviewLockedReason.PREVIEW_UNAVAILABLE,
                "Paid document page count is invalid");
    }
}