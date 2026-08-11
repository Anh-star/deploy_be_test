package com.cmcu.itstudy.dto.document;

/**
 * Preview delivery mode returned by the secure paid preview endpoint.
 *
 * <ul>
 *   <li>{@link #FULL} — the caller has full access and the response body is
 *       the original (signed URL / private object bytes).</li>
 *   <li>{@link #LIMITED} — the caller has no purchase, the document is paid,
 *       and the response body is a server-generated derivative PDF
 *       containing only the allowed pages.</li>
 *   <li>{@link #LOCKED} — the caller has no purchase and the document is
 *       paid, but the file type / page count does not allow a safe
 *       derivative (non-PDF, single page, empty). The response body is a
 *       JSON descriptor so the client can render a locked placeholder.</li>
 * </ul>
 */
public enum PreviewMode {
    FULL,
    LIMITED,
    LOCKED
}