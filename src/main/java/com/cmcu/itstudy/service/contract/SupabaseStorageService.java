package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.storage.SignedUploadTarget;
import com.cmcu.itstudy.dto.storage.StorageObjectInfo;

/**
 * Backend-only Supabase storage client.
 *
 * <p>This service is the single boundary that calls Supabase HTTP. It must
 * never log the service role key, apikey, Authorization header, signed
 * token, or signed URL.
 *
 * <p>Methods in this service must NOT be invoked inside a database
 * transaction.
 */
public interface SupabaseStorageService {

    /**
     * Create a signed upload target for an object in a bucket.
     *
     * @param bucket bucket name (already resolved from
     *               {@link com.cmcu.itstudy.config.SupabaseProperties})
     * @param path   object path (already validated, server-generated)
     * @return signed upload target returned by Supabase
     */
    SignedUploadTarget createSignedUploadTarget(String bucket, String path);

    /**
     * Fetch authoritative object metadata via the official Supabase
     * Storage object-info endpoint
     * ({@code GET /storage/v1/object/info/{bucket}/{path}}).
     *
     * <p>Backed by the {@code FileObjectV2} type in
     * {@code supabase/storage-js} (see official source). The returned
     * {@link StorageObjectInfo} only carries fields the StudyIT code
     * actually consumes; the raw response body, signed URL, and
     * Supabase credentials are never propagated upward.
     *
     * <p>This call uses the same backend credentials contract as
     * {@link #createSignedUploadTarget} (service role key in
     * {@code Authorization} + {@code apikey}). The call is rate
     * limited by the same studyIT timeouts (5s connect / 10s read).
     *
     * <p>This method MUST NOT be invoked inside a database transaction.
     *
     * @param bucket bucket name
     * @param path   object path (server-generated, never raw filename)
     * @return verified object info, or a domain exception is thrown
     * @throws com.cmcu.itstudy.handle.StorageObjectNotFoundException when
     *         the object is missing (HTTP 404 or empty body)
     * @throws com.cmcu.itstudy.handle.SignedUploadTargetFailedException
     *         on timeout, network failure, or any non-recoverable
     *         Supabase error (status code is logged server-side but
     *         never surfaced to the caller)
     */
    StorageObjectInfo getObjectInfo(String bucket, String path);

    /**
     * Download the raw bytes of a private-bucket object via the
     * Supabase storage endpoint
     * ({@code GET /storage/v1/object/{bucket}/{path}}).
     *
     * <p>This is the read counterpart of
     * {@link #createSignedUploadTarget(String, String)}. It exists so the
     * secure paid preview pipeline can stream the original bytes out of a
     * private bucket through the access-controlled
     * {@code GET /api/documents/{id}/preview} endpoint without ever
     * surfacing a signed URL, public URL, token, or private storage path
     * to the client.
     *
     * <p>Security contract:
     * <ul>
     *   <li>Buckets and paths are resolved server-side from
     *       {@link com.cmcu.itstudy.entity.DocumentFile}; the caller
     *       cannot influence which object is downloaded.</li>
     *   <li>Authentication is the service-role key configured in
     *       {@link com.cmcu.itstudy.config.SupabaseProperties}; the
     *       key, the {@code Authorization} header, the response body,
     *       and any signed URL are never logged.</li>
     *   <li>The response is bounded by the StudyIT 25&nbsp;MB limit.
     *       Anything larger is mapped to
     *       {@link com.cmcu.itstudy.handle.PreviewFileTooLargeException}.</li>
     *   <li>404 → {@link com.cmcu.itstudy.handle.StorageObjectNotFoundException}.</li>
     *   <li>401 / 403 / timeout / network → safe
     *       {@link com.cmcu.itstudy.handle.SignedUploadTargetFailedException}
     *       (no Supabase payload echoed back).</li>
     * </ul>
     *
     * <p>This method MUST NOT be invoked inside a database transaction.
     *
     * @param bucket bucket name (must be a configured private bucket)
     * @param path   object path (server-resolved, never raw filename)
     * @return raw object bytes (max 25&nbsp;MB by configuration)
     * @throws com.cmcu.itstudy.handle.StorageObjectNotFoundException when
     *         the object is missing (HTTP 404 or empty body)
     * @throws com.cmcu.itstudy.handle.SignedUploadTargetFailedException
     *         on timeout, network failure, or any non-recoverable
     *         Supabase error
     * @throws com.cmcu.itstudy.handle.PreviewFileTooLargeException when
     *         the object exceeds the 25&nbsp;MB preview cap
     */
    byte[] downloadPrivateObject(String bucket, String path);

    /**
     * Delete an object from a Supabase storage bucket.
     *
     * @param bucket bucket name
     * @param path   object path
     */
    void deleteObject(String bucket, String path);
}
