package com.cmcu.itstudy.dto.office;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7B.1 — pins the {@link OfficeConversionResult} contract
 * that the {@code DocumentPreviewArtifactProcessor} now relies on
 * after the duplicate validation in {@code processFull} was
 * removed.
 *
 * <p>The processor used to re-run {@code
 * OfficePdfValidationService.validateAndCountPages(tempPdf)} on the
 * uploaded-buffer PDF, then on the converter's output dir, doubling
 * the peak resident PDF memory. After 7B.1 the processor trusts
 * {@link OfficeConversionResult#pageCount()} directly. Three
 * invariants guarantee that trust is safe:</p>
 *
 * <ol>
 *   <li>The record constructor rejects a non-positive
 *       {@code pageCount}, so any successfully constructed
 *       {@code OfficeConversionResult} has {@code pageCount > 0}.</li>
 *   <li>The constructor rejects an empty {@code pdfBytes} array,
 *       so {@code pdfBytes} is always non-null and non-empty when
 *       a result is returned.</li>
 *   <li>{@code outputBytes == pdfBytes.length} is enforced at
 *       construction time, so the size cannot drift between
 *       construction and use.</li>
 * </ol>
 */
class OfficeConversionResultPageCountContractTest {

    @Test
    @DisplayName("constructor accepts a positive pageCount and a non-empty byte array")
    void constructorAcceptsPositivePageCount() {
        byte[] pdf = new byte[] { (byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46 };
        OfficeConversionResult result = new OfficeConversionResult(
                pdf, 7, pdf.length, Duration.ofMillis(123),
                com.cmcu.itstudy.enums.AllowedDocumentFileType.DOCX);
        assertEquals(7, result.pageCount());
        assertEquals(pdf.length, result.outputBytes());
    }

    @Test
    @DisplayName("constructor rejects pageCount == 0")
    void constructorRejectsZeroPageCount() {
        byte[] pdf = new byte[] { 0x25, 0x50, 0x44, 0x46 };
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new OfficeConversionResult(
                        pdf, 0, pdf.length, Duration.ZERO,
                        com.cmcu.itstudy.enums.AllowedDocumentFileType.DOC));
        assertTrue(error.getMessage().toLowerCase().contains("pagecount"),
                "error message must name pageCount");
    }

    @Test
    @DisplayName("constructor rejects negative pageCount")
    void constructorRejectsNegativePageCount() {
        byte[] pdf = new byte[] { 0x25, 0x50, 0x44, 0x46 };
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult(
                        pdf, -3, pdf.length, Duration.ZERO,
                        com.cmcu.itstudy.enums.AllowedDocumentFileType.DOCX));
    }

    @Test
    @DisplayName("constructor rejects empty pdfBytes")
    void constructorRejectsEmptyBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult(
                        new byte[0], 1, 0, Duration.ZERO,
                        com.cmcu.itstudy.enums.AllowedDocumentFileType.DOC));
    }

    @Test
    @DisplayName("constructor rejects null pdfBytes")
    void constructorRejectsNullBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new OfficeConversionResult(
                        null, 1, 0, Duration.ZERO,
                        com.cmcu.itstudy.enums.AllowedDocumentFileType.DOCX));
    }
}