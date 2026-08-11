package com.cmcu.itstudy.dto.storage;

import java.time.LocalDateTime;

/**
 * Minimal source-proven metadata returned by the official Supabase
 * Storage object-info endpoint.
 *
 * <p>Backed by the {@code GET /storage/v1/object/info/{bucket}/{path}}
 * endpoint exposed by the storage-js client
 * ({@code FileObjectV2} type, see official
 * {@code supabase/supabase-js} / {@code supabase/storage-js}). The wire
 * format returns camelCase fields, so this DTO maps directly to:
 * <ul>
 *   <li>{@code size} → {@link #sizeBytes} (number, bytes)</li>
 *   <li>{@code contentType} → {@link #contentType} (string, MIME)</li>
 *   <li>{@code lastModified} → {@link #lastModified} (string, ISO 8601)</li>
 *   <li>{@code etag} → {@link #etag} (string, opaque cache tag)</li>
 * </ul>
 *
 * <p>Only the fields the binder actually verifies
 * ({@code sizeBytes}, {@code contentType}) are guaranteed to be present.
 * The other two are populated when the source response includes them and
 * are otherwise {@code null}.
 *
 * <p>This DTO does NOT carry:
 * <ul>
 *   <li>any signed URL,</li>
 *   <li>the raw Supabase response body,</li>
 *   <li>the service role key, apikey, or Authorization header.</li>
 * </ul>
 */
public final class StorageObjectInfo {

    private final long sizeBytes;
    private final String contentType;
    private final String etag;
    private final LocalDateTime lastModified;

    public StorageObjectInfo(long sizeBytes,
                             String contentType,
                             String etag,
                             LocalDateTime lastModified) {
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.etag = etag;
        this.lastModified = lastModified;
    }

    /** Returns the object size in bytes as reported by Supabase. */
    public long sizeBytes() {
        return sizeBytes;
    }

    /**
     * Returns the object MIME type as reported by Supabase, or
     * {@code null} when Supabase did not expose one.
     */
    public String contentType() {
        return contentType;
    }

    /**
     * Returns the opaque entity tag from Supabase, or {@code null} when
     * the source response did not include it.
     */
    public String etag() {
        return etag;
    }

    /**
     * Returns the last modified timestamp parsed from the Supabase
     * {@code last_modified} ISO 8601 string, or {@code null} when the
     * source response did not include it (or it could not be parsed).
     */
    public LocalDateTime lastModified() {
        return lastModified;
    }
}
