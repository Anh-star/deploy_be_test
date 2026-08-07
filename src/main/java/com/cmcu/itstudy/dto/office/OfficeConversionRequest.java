package com.cmcu.itstudy.dto.office;

import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.handle.OfficeConversionInputTooLargeException;
import com.cmcu.itstudy.handle.OfficeConversionInvalidInputException;
import com.cmcu.itstudy.handle.OfficeConversionUnsupportedFormatException;

import java.util.Objects;

/**
 * Validated request handed to {@code LibreOfficeDocumentConverter}.
 *
 * <p>The converter accepts only {@link AllowedDocumentFileType#DOC DOC}
 * and {@link AllowedDocumentFileType#DOCX DOCX} inputs. Other
 * extensions surface a terminal
 * {@link OfficeConversionUnsupportedFormatException} before any process
 * is started.</p>
 *
 * <p>Empty or null input bytes surface a terminal
 * {@link OfficeConversionInvalidInputException} (codes {@code EMPTY_INPUT}
 * or {@code NULL_INPUT}) — distinct from an unsupported file extension.</p>
 *
 * <p>Instances are immutable. The {@code bytes} reference may be shared
 * but must not be mutated by callers after the request is submitted.</p>
 *
 * @param bytes               the original Office bytes (non-null, non-empty)
 * @param fileType            validated DOC / DOCX enum value
 * @param correlationId       optional opaque id for log correlation;
 *                            never logged verbatim, only as a safe prefix
 * @throws OfficeConversionInvalidInputException when bytes is null or empty
 * @throws OfficeConversionUnsupportedFormatException when fileType is not DOC/DOCX
 */
public record OfficeConversionRequest(
        byte[] bytes,
        AllowedDocumentFileType fileType,
        String correlationId) {

    public OfficeConversionRequest {
        if (bytes == null) {
            throw new OfficeConversionInvalidInputException("NULL_INPUT",
                    "Office input bytes must not be null");
        }
        if (bytes.length == 0) {
            throw new OfficeConversionInvalidInputException("EMPTY_INPUT",
                    "Office input is empty");
        }
        Objects.requireNonNull(fileType, "fileType must not be null");
        if (fileType != AllowedDocumentFileType.DOC
                && fileType != AllowedDocumentFileType.DOCX) {
            throw new OfficeConversionUnsupportedFormatException(
                    "Office input type " + fileType + " is not supported by the converter");
        }
        if (correlationId == null) {
            correlationId = "";
        }
    }

    /**
     * @return the validated file extension (lowercase, no leading dot)
     */
    public String extension() {
        return fileType.extension();
    }

    /**
     * @return the validated canonical MIME type
     */
    public String mimeType() {
        return fileType.mimeType();
    }

    /**
     * Enforce the configured input size cap. Throws
     * {@link OfficeConversionInputTooLargeException} on overflow.
     */
    public OfficeConversionRequest enforceMaxInputBytes(long maxInputBytes) {
        if (bytes.length > maxInputBytes) {
            throw new OfficeConversionInputTooLargeException(
                    "Office input size " + bytes.length
                            + " exceeds maximum " + maxInputBytes);
        }
        return this;
    }
}
