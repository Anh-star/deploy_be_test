package com.cmcu.itstudy.dto.office;

import java.time.Duration;

/**
 * Immutable result returned by {@code LibreOfficeDocumentConverter}.
 *
 * <p>The converter never returns the temporary paths it used, never
 * returns the LibreOffice process, and never returns the original
 * Office bytes. Callers receive the canonical PDF bytes plus the
 * metrics needed for downstream persistence (page count, output size,
 * duration).</p>
 *
 * @param pdfBytes    canonical PDF bytes (defensive copy)
 * @param pageCount   number of pages in the resulting PDF (&gt; 0)
 * @param outputBytes size of {@code pdfBytes} in bytes
 * @param duration    wall-clock conversion duration
 * @param fileType    the validated Office type that was converted
 */
public record OfficeConversionResult(
        byte[] pdfBytes,
        int pageCount,
        long outputBytes,
        Duration duration,
        com.cmcu.itstudy.enums.AllowedDocumentFileType fileType) {

    public OfficeConversionResult {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("pdfBytes must not be null or empty");
        }
        if (pageCount <= 0) {
            throw new IllegalArgumentException("pageCount must be positive");
        }
        if (outputBytes != pdfBytes.length) {
            throw new IllegalArgumentException("outputBytes must match pdfBytes length");
        }
        if (duration == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
        if (fileType == null) {
            throw new IllegalArgumentException("fileType must not be null");
        }
        byte[] copy = new byte[pdfBytes.length];
        System.arraycopy(pdfBytes, 0, copy, 0, pdfBytes.length);
        pdfBytes = copy;
    }
}
