package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.office.OfficeConversionResult;
import com.cmcu.itstudy.dto.office.OfficeConversionRequest;

/**
 * Phase&nbsp;O1 contract for converting validated DOC / DOCX bytes
 * into a canonical PDF preview.
 *
 * <p>Implementations must accept only DOC and DOCX inputs. Any other
 * extension surfaces a typed terminal
 * {@link com.cmcu.itstudy.handle.OfficeConversionUnsupportedFormatException}
 * before any process is started.</p>
 *
 * <p>The contract never receives or returns raw public URLs, Supabase
 * paths or service-role secrets. It accepts validated Office bytes
 * and returns immutable PDF bytes plus metrics.</p>
 */
public interface OfficeDocumentConverter {

    /**
     * Convert the supplied request into a PDF.
     *
     * @param request validated request (non-null, non-empty, supported type)
     * @return immutable result containing the canonical PDF bytes
     */
    OfficeConversionResult convert(OfficeConversionRequest request);
}
