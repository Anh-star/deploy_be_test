package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.PendingStorageUpload;
import com.cmcu.itstudy.enums.PendingUploadStatus;
import com.cmcu.itstudy.repository.custom.PendingStorageUploadClaimRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingStorageUploadRepository
        extends JpaRepository<PendingStorageUpload, UUID>,
        PendingStorageUploadClaimRepository {

    Optional<PendingStorageUpload> findByUploadId(UUID uploadId);

    List<PendingStorageUpload> findByStatus(PendingUploadStatus status);
}