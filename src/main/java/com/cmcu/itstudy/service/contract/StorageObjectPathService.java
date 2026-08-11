package com.cmcu.itstudy.service.contract;

import java.util.UUID;

/**
 * Builds server-side object paths for Supabase Storage. The raw filename,
 * user email, and user-supplied metadata are NEVER used as the object path;
 * only authenticated {@code userId} and a server-generated {@code uploadId}
 * are referenced.
 */
public interface StorageObjectPathService {

    String PAID_PREFIX = "paid/";

    /**
     * Build the canonical paid-upload object path:
     * {@code paid/{userId}/{uploadId}.{extension}}.
     *
     * <p>The result is fully deterministic for the given inputs, contains no
     * user-supplied raw filename, no timestamp, no user email, and no path
     * traversal characters.
     */
    String buildPaidUploadPath(UUID userId, UUID uploadId, String canonicalExtension);
}
