package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.config.SupabaseProperties;

/**
 * Validates that all Supabase configuration values required by the
 * signed-upload-target flow are present and non-blank.
 *
 * <p>This service issues NO HTTP request and does NOT log or otherwise
 * surface config values. Failures are mapped to {@link
 * com.cmcu.itstudy.handle.SignedUploadTargetFailedException} with a
 * category-safe message that does NOT reveal the secret.
 */
public interface SupabaseConfigValidatorService {

    /**
     * Validate that the properties needed for the signed-upload-target
     * call are present and non-blank.
     *
     * @param properties Supabase properties read from the environment
     * @return the validated private bucket name (also asserted non-blank)
     * @throws com.cmcu.itstudy.handle.SignedUploadTargetFailedException
     *         if any required value is null or blank
     */
    String validateSignedUploadTargetConfig(SupabaseProperties properties);
}
