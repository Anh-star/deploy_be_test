package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.handle.OfficeConversionInvalidOutputException;
import com.cmcu.itstudy.handle.OfficeConversionOutputTooLargeException;

import java.nio.file.Path;

/**
 * Validates the PDF produced by {@code LibreOfficeDocumentConverter}.
 *
 * <p>The validator confirms that the file exists, is a regular file,
 * is non-empty, fits within the configured hard size cap, starts with
 * the {@code %PDF-} magic bytes, loads cleanly via PDFBox and has at
 * least one page. Any failure throws a typed terminal exception that
 * carries a safe failure code for the O3 mapping.</p>
 *
 * <p>The validator always closes its PDFBox document before returning,
 * even on failure paths.</p>
 */
public interface OfficePdfValidationService {

    /**
     * Validate the file at {@code pdfPath}.
     *
     * @param pdfPath absolute path of the candidate PDF (non-null)
     * @return the validated page count (&gt; 0)
     * @throws OfficeConversionInvalidOutputException for missing file,
     *         bad signature, PDFBox load failure, or zero-page output
     * @throws OfficeConversionOutputTooLargeException when the output
     *         exceeds the configured hard cap
     */
    int validateAndCountPages(Path pdfPath);
}
