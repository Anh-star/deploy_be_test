package com.cmcu.itstudy.handle;

import com.cmcu.itstudy.dto.document.PreviewLockedReason;

/**
 * Thrown when a paid preview cannot be produced because the underlying
 * private object is missing, malformed, or in an unsupported format
 * (e.g. an empty PDF, a one-page paid PDF, or a non-PDF paid object).
 *
 * <p>The exception carries a {@link PreviewLockedReason} so the
 * controller / exception handler can render the right copy for the
 * failure cause WITHOUT consulting any mutable singleton state. The
 * reason is request-local: every throw site passes the reason it
 * computed for THIS request.</p>
 *
 * <p>This exception is the input to the locked-state branch of the
 * preview response. The {@link com.cmcu.itstudy.handle.GlobalExceptionHandler}
 * is NOT involved for locked responses — the controller translates this
 * exception into a 200 with a {@code LOCKED} JSON body so that the
 * client can render the buy-now CTA without treating it as an HTTP
 * error.</p>
 */
public class PaidPreviewUnavailableException extends RuntimeException {

    private final PreviewLockedReason reason;

    /**
     * Backwards-compatible constructor preserved for older call sites.
     * Defaults the {@link #reason} to {@link PreviewLockedReason#PREVIEW_UNAVAILABLE}.
     */
    public PaidPreviewUnavailableException(String message) {
        super(message);
        this.reason = PreviewLockedReason.PREVIEW_UNAVAILABLE;
    }

    /**
     * Construct an exception that carries a request-local locked
     * reason. Prefer this overload whenever the call site can identify
     * the precise cause so the controller can render specific copy.
     *
     * @param reason locked reason — MUST be non-null
     * @param message human-readable Vietnamese message
     */
    public PaidPreviewUnavailableException(PreviewLockedReason reason, String message) {
        super(message);
        this.reason = reason == null ? PreviewLockedReason.PREVIEW_UNAVAILABLE : reason;
    }

    /** Read-only view of the locked reason. Never null. */
    public PreviewLockedReason getReason() {
        return reason;
    }
}