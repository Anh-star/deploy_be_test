package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.StorageCleanupTask;
import com.cmcu.itstudy.enums.StorageCleanupTaskStatus;
import com.cmcu.itstudy.repository.custom.StorageCleanupTaskClaimRepository;
import com.cmcu.itstudy.repository.custom.StorageCleanupTaskInsertRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StorageCleanupTaskRepository
        extends JpaRepository<StorageCleanupTask, Long>,
        StorageCleanupTaskClaimRepository,
        StorageCleanupTaskInsertRepository {

    List<StorageCleanupTask> findByStatus(StorageCleanupTaskStatus status);

    /**
     * Returns the active cleanup task for the given key, if any.
     * Active = status in PENDING, IN_PROGRESS, RETRY.
     */
    @Query("""
            select t from StorageCleanupTask t
            where t.targetBucket = :targetBucket
              and t.targetPath = :targetPath
              and t.reason = :reason
              and t.status in (
                  com.cmcu.itstudy.enums.StorageCleanupTaskStatus.PENDING,
                  com.cmcu.itstudy.enums.StorageCleanupTaskStatus.IN_PROGRESS,
                  com.cmcu.itstudy.enums.StorageCleanupTaskStatus.RETRY
              )
            order by t.id asc
            """)
    List<StorageCleanupTask> findActiveTasks(
            @Param("targetBucket") String targetBucket,
            @Param("targetPath") String targetPath,
            @Param("reason") com.cmcu.itstudy.enums.StorageCleanupReason reason);

    default Optional<StorageCleanupTask> findActiveTask(
            String targetBucket,
            String targetPath,
            com.cmcu.itstudy.enums.StorageCleanupReason reason) {
        List<StorageCleanupTask> tasks = findActiveTasks(targetBucket, targetPath, reason);
        return tasks.isEmpty() ? Optional.empty() : Optional.of(tasks.get(0));
    }
}