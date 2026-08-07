package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.enums.AllowedDocumentFileType;

/**
 * Validates uploaded file metadata against the allowlist defined in
 * {@link AllowedDocumentFileType}.
 *
 * <p>This service issues NO HTTP requests and does NOT touch the database.
 * All side-effect-free.
 */
public interface PaidUploadFileValidatorService {

    long MAX_SIZE_BYTES = 25L * 1024L * 1024L;

    /**
     * Returns the canonical {@link AllowedDocumentFileType} if and only if
     * the filename, declared MIME type, and size all pass validation.
     *
     * @param fileName  original filename from the request (may contain a path)
     * @param mimeType  declared Content-Type from the request
     * @param sizeBytes declared size in bytes (must be &gt; 0 and &le; 25 MiB)
     */
    AllowedDocumentFileType validate(String fileName, String mimeType, Long sizeBytes);
}
