package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.SupabaseProperties;
import com.cmcu.itstudy.handle.SignedUploadTargetFailedException;
import com.cmcu.itstudy.service.contract.SupabaseConfigValidatorService;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link SupabaseConfigValidatorService}.
 *
 * <h2>Public error messages</h2>
 * <p>Public exception messages are intentionally generic; they MUST NOT
 * reveal which property is missing or contain any configuration value.
 * The internal exception category is preserved so server-side logs can
 * still distinguish the failure cause without exposing it.
 */
@Service
public class SupabaseConfigValidatorServiceImpl implements SupabaseConfigValidatorService {

    @Override
    public String validateSignedUploadTargetConfig(SupabaseProperties properties) {
        if (properties == null) {
            throw signedUploadTargetFailed("missing-config");
        }
        if (isBlank(properties.getUrl())) {
            throw signedUploadTargetFailed("missing-url");
        }
        if (isBlank(properties.getServiceRoleKey())) {
            throw signedUploadTargetFailed("missing-service-role-key");
        }
        if (isBlank(properties.getPrivateDocumentBucket())) {
            throw signedUploadTargetFailed("missing-bucket");
        }
        return properties.getPrivateDocumentBucket();
    }

    private static SignedUploadTargetFailedException signedUploadTargetFailed(
            String internalCategory) {
        // The category name is used for server-side correlation only.
        // The public message is a single generic string that does not
        // expose any configuration value.
        return new SignedUploadTargetFailedException(
                "Storage service is not configured", internalCategory);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}