package com.cmcu.itstudy.enums;

import java.util.Optional;

/**
 * Whitelist of allowed file types for paid document uploads.
 *
 * <p>Each value pairs a canonical {@code extension} (lowercase, no leading
 * dot) with a canonical {@code mimeType}. The pair must match; mismatched
 * pairs in the request payload are rejected during validation.
 *
 * <p>This enum does NOT issue any HTTP request to Supabase. It only
 * describes the allowlist metadata.
 */
public enum AllowedDocumentFileType {

    PDF("pdf", "application/pdf"),
    DOC("doc", "application/msword"),
    DOCX("docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PPT("ppt", "application/vnd.ms-powerpoint"),
    PPTX("pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private final String extension;
    private final String mimeType;

    AllowedDocumentFileType(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public static Optional<AllowedDocumentFileType> fromExtension(String ext) {
        if (ext == null) {
            return Optional.empty();
        }
        String normalized = ext.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        for (AllowedDocumentFileType t : values()) {
            if (t.extension.equals(normalized)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    public static Optional<AllowedDocumentFileType> fromMimeType(String mime) {
        if (mime == null) {
            return Optional.empty();
        }
        String normalized = mime.trim().toLowerCase(java.util.Locale.ROOT);
        for (AllowedDocumentFileType t : values()) {
            if (t.mimeType.equalsIgnoreCase(normalized)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}