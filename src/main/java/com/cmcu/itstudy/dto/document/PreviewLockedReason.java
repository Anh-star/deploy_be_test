package com.cmcu.itstudy.dto.document;

/**
 * Reason why a paid preview was locked instead of returning a limited PDF
 * derivative. Returned to the frontend so the buy-now CTA copy matches the
 * actual server-side reason.
 */
public enum PreviewLockedReason {

    /**
     * Default reason: the caller does not yet own the paid document.
     */
    PURCHASE_REQUIRED,

    /**
     * Paid PDF that has exactly one page. Returning any portion of a
     * single-page file would still expose the whole document, so the
     * preview is fully locked.
     */
    ONE_PAGE_PAID_DOCUMENT,

    /**
     * Paid file that is not a PDF. The backend does not have a generic
     * document derivative pipeline, so non-PDF paid files stay locked
     * until purchase.
     */
    NON_PDF_PAID_DOCUMENT,

    /**
     * Generic fallback for malformed / unreadable PDFs or zero-page files.
     */
    PREVIEW_UNAVAILABLE
}