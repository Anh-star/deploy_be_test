package com.cmcu.itstudy.dto.storage;

/**
 * Internal value object for a Supabase signed upload target.
 *
 * <p>Only {@code token} is kept. The token is the short-lived
 * Supabase-issued value extracted from the {@code url} query parameter of
 * the create-signed-upload-url response.
 *
 * <p>This DTO does NOT carry:
 * <ul>
 *   <li>the raw Supabase response body,</li>
 *   <li>the raw signed URL,</li>
 *   <li>the service role key,</li>
 *   <li>the apikey,</li>
 *   <li>any Authorization header.</li>
 * </ul>
 *
 * <p>The StudyIT pending-upload bind deadline ({@code now + 15 minutes})
 * is computed in the orchestrator and is NOT a Supabase-signed-token
 * expiry. See {@link PaidUploadTargetResponseDto} for the bind deadline.
 */
public record SignedUploadTarget(String token) {
}
