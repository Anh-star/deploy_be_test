package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.handle.FileTooLargeException;
import com.cmcu.itstudy.handle.InvalidFileNameException;
import com.cmcu.itstudy.handle.InvalidFileSizeException;
import com.cmcu.itstudy.handle.MimeExtensionMismatchException;
import com.cmcu.itstudy.handle.UnsupportedFileTypeException;
import com.cmcu.itstudy.service.contract.PaidUploadFileValidatorService;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Default implementation of {@link PaidUploadFileValidatorService}.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>Filename must be non-blank.</li>
 *   <li>Filename must contain a single-dot extension separator; the
 *       last extension must be in the allowlist.</li>
 *   <li>Filename must not contain path traversal {@code ".."} or absolute
 *       path characters.</li>
 *   <li>Declared MIME type must be in the allowlist.</li>
 *   <li>Extension and MIME type must resolve to the SAME
 *       {@link AllowedDocumentFileType}.</li>
 *   <li>Size must be non-null, positive, and &le; 25 MiB.</li>
 * </ul>
 *
 * <p>Validation never trusts the raw Content-Type alone and never uses the
 * raw filename as the object path.
 */
@Service
public class PaidUploadFileValidatorServiceImpl implements PaidUploadFileValidatorService {

    @Override
    public AllowedDocumentFileType validate(String fileName, String mimeType, Long sizeBytes) {
        if (sizeBytes == null) {
            throw new InvalidFileSizeException("sizeBytes is required");
        }
        if (sizeBytes <= 0) {
            throw new InvalidFileSizeException("sizeBytes must be greater than 0");
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new FileTooLargeException(sizeBytes, MAX_SIZE_BYTES);
        }

        if (fileName == null || fileName.isBlank()) {
            throw new InvalidFileNameException("fileName must not be blank");
        }

        // Normalize and reject path traversal / separators.
        String trimmed = fileName.trim();
        if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")
                || trimmed.startsWith(".") || trimmed.contains("\0")) {
            throw new InvalidFileNameException("fileName contains unsafe characters");
        }

        // Disallow double-extension patterns where the trailing extension is
        // NOT in the allowlist, even if some intermediate segment looks
        // innocent (e.g., "report.pdf.exe" -> only "exe" should be checked,
        // and it must not be allowed).
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int lastDot = lower.lastIndexOf('.');
        if (lastDot < 0 || lastDot == lower.length() - 1) {
            throw new InvalidFileNameException("fileName must contain an extension");
        }
        String trailingExtension = lower.substring(lastDot + 1);

        // If there are multiple dots (e.g., "report.tar.pdf"), only the
        // trailing extension matters. The intermediate segments are ignored
        // for type determination. However, we still want to refuse double-
        // extensions like "evil.exe.pdf" ONLY when the trailing extension is
        // disallowed.
        Optional<AllowedDocumentFileType> byExtension =
                AllowedDocumentFileType.fromExtension(trailingExtension);
        if (byExtension.isEmpty()) {
            throw new UnsupportedFileTypeException(
                    "Extension '" + trailingExtension + "' is not allowed");
        }

        // Resolve MIME separately; if MIME is missing or not in allowlist, reject.
        if (mimeType == null || mimeType.isBlank()) {
            throw new InvalidFileNameException("mimeType must not be blank");
        }
        Optional<AllowedDocumentFileType> byMime = AllowedDocumentFileType.fromMimeType(mimeType);
        if (byMime.isEmpty()) {
            throw new UnsupportedFileTypeException(
                    "MIME type '" + mimeType + "' is not allowed");
        }

        // Mismatch between extension-resolved type and MIME-resolved type.
        if (byExtension.get() != byMime.get()) {
            throw new MimeExtensionMismatchException(
                    "Extension '" + trailingExtension
                            + "' does not match MIME type '" + mimeType + "'");
        }

        // Use the basename (filename without any path prefix) — already
        // enforced by the early rejection of "/" and "\\".
        return byExtension.get();
    }
}