package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.storage.PaidUploadTargetRequestDto;
import com.cmcu.itstudy.dto.storage.PaidUploadTargetResponseDto;
import com.cmcu.itstudy.entity.User;

/**
 * Orchestrator for the paid upload target flow.
 *
 * <p>The remote Supabase call runs OUTSIDE any database transaction. The
 * database registration of {@link com.cmcu.itstudy.entity.PendingStorageUpload}
 * runs in a short transaction AFTER the Supabase target is successfully
 * created. The response is returned to the caller only after the DB commit.
 */
public interface PaidUploadTargetOrchestrator {

    PaidUploadTargetResponseDto createTarget(
            User currentUser, PaidUploadTargetRequestDto request);
}
