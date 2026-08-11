package com.cmcu.itstudy.handle;

/**
 * Thrown when the Supabase object-info endpoint reports that the target
 * object does not exist (HTTP 404 from
 * {@code GET /storage/v1/object/info/{bucket}/{path}}).
 *
 * <p>For a paid-create request this means the user never actually
 * uploaded the binary to the signed URL, so the {@code PendingStorageUpload}
 * row stays {@code PENDING} until expiration. The cause category is
 * logged server-side but never surfaced verbatim to the client.
 */
public class StorageObjectNotFoundException extends RuntimeException {

    public StorageObjectNotFoundException(String message) {
        super(message);
    }

    public StorageObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
