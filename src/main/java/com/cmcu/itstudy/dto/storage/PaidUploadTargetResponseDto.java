package com.cmcu.itstudy.dto.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for the paid-upload-target endpoint.
 *
 * <p>Does NOT include the service role key, the raw Supabase apikey, the
 * Authorization header, the raw signed URL, or any Supabase-side token
 * expiry. Only the uploadId, bucket, path, signed token, and the StudyIT
 * pending-upload bind deadline are returned.
 *
 * <p><b>{@link #expiresAt} is the StudyIT pending-upload bind deadline</b>
 * ({@code now + 15 minutes}). It is NOT the Supabase signed-token TTL.
 * Authors that consume this field must NOT interpret it as the moment
 * after which the Supabase-issued signed URL becomes invalid.
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaidUploadTargetResponseDto {

    private UUID uploadId;
    private String bucket;
    private String path;
    private String token;
    private LocalDateTime expiresAt;
}
