package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.handle.InvalidFileNameException;
import com.cmcu.itstudy.service.contract.StorageObjectPathService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Default implementation of {@link StorageObjectPathService}.
 *
 * <p>Path format: {@code paid/{userId}/{uploadId}.{extension}}.
 *
 * <p>Defense-in-depth checks:
 * <ul>
 *   <li>Rejects userId/uploadId/extension with path-separator characters.</li>
 *   <li>Rejects null / blank inputs.</li>
 *   <li>The {@code extension} here is the canonical form coming from
 *       {@link com.cmcu.itstudy.enums.AllowedDocumentFileType#extension()}.</li>
 * </ul>
 */
@Service
public class StorageObjectPathServiceImpl implements StorageObjectPathService {

    @Override
    public String buildPaidUploadPath(UUID userId, UUID uploadId, String canonicalExtension) {
        if (userId == null || uploadId == null) {
            throw new InvalidFileNameException("userId and uploadId must not be null");
        }
        if (canonicalExtension == null || canonicalExtension.isBlank()) {
            throw new InvalidFileNameException("extension must not be blank");
        }
        if (canonicalExtension.contains("/") || canonicalExtension.contains("\\")
                || canonicalExtension.contains("..") || canonicalExtension.contains("\0")
                || canonicalExtension.contains(".")) {
            throw new InvalidFileNameException("extension contains unsafe characters");
        }
        return PAID_PREFIX + userId + "/" + uploadId + "." + canonicalExtension;
    }
}
