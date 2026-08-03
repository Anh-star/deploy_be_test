package com.cmcu.itstudy.handle;

/**
 * Raised when a contributor tries to change a document's pricing
 * (isPaid / price) after the document has already been locked by a
 * successful non-owner purchase.
 *
 * <p>Maps to HTTP 409 Conflict via {@code GlobalExceptionHandler}.
 * Intentionally distinct from
 * {@link WithdrawalStateConflictException} / other domain conflict
 * exceptions because the semantic — "pricing is permanently locked for
 * this document" — belongs to the document module, not the withdrawal
 * module.
 */
public class DocumentPricingLockedException extends RuntimeException {

    public DocumentPricingLockedException(String message) {
        super(message);
    }
}