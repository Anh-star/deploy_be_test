package com.cmcu.itstudy.handle;

/**
 * Thrown when an Office input is structurally invalid in a way that
 * has nothing to do with the declared file type. This is a
 * TERMINAL failure: the same input, retried later, will fail in the
 * same way.
 *
 * <p>Distinct from
 * {@link OfficeConversionUnsupportedFormatException}, which is reserved
 * for inputs whose file extension or MIME type is not part of the
 * DOC / DOCX whitelist. {@code OfficeConversionInvalidInputException}
 * covers failure codes such as:</p>
 * <ul>
 *   <li>{@code EMPTY_INPUT} — the byte array length is zero;</li>
 *   <li>{@code NULL_INPUT} — the byte array reference itself is null.</li>
 * </ul>
 */
public class OfficeConversionInvalidInputException extends OfficeConversionTerminalException {

    public OfficeConversionInvalidInputException(String failureCode, String message) {
        super(failureCode, message);
    }
}
