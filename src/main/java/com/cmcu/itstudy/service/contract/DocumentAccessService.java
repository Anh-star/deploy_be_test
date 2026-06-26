package com.cmcu.itstudy.service.contract;

import java.util.UUID;

public interface DocumentAccessService {

    boolean hasAccess(UUID userId, UUID documentId);

    void grantAccess(UUID userId, UUID documentId, UUID paymentId);
}
