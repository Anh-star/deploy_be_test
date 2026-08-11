package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.StorageCleanupTask;
import com.cmcu.itstudy.enums.StorageCleanupReason;
import com.cmcu.itstudy.repository.PendingStorageUploadRepository;
import com.cmcu.itstudy.repository.custom.PendingUploadTransitionTarget;
import com.cmcu.itstudy.service.contract.PendingUploadFailureService;
import com.cmcu.itstudy.service.contract.StorageCleanupTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link PendingUploadFailureService}.
 *
 * <p>Each public method runs in its own {@code REQUIRES_NEW} transaction
 * so the caller (the orchestrator) can stay {@code NOT_SUPPORTED}.
 *
 * <h2>Atomicity</h2>
 * <p>The pending-row UPDATE and the cleanup-task INSERT run in the SAME
 * transaction. The state transition is performed by the race-safe
 * {@link com.cmcu.itstudy.repository.custom.PendingStorageUploadClaimRepository}
 * — a single SQL Server
 * {@code UPDATE ... WITH (UPDLOCK, ROWLOCK) ... OUTPUT inserted.*}
 * statement that:
 * <ul>
 *   <li>filters on {@code status = 'PENDING' AND expires_at ...}
 *       (state-machine predicate);</li>
 *   <li>atomically writes the new status;</li>
 *   <li>returns the authoritative bucket and path that were visible
 *       to the database engine at the exact moment of transition.</li>
 * </ul>
 * This service does NOT use
 * {@code pendingUploadRepository.save(entity)} to perform state
 * transitions; that pattern was load-modify-save with no
 * conditional predicate and was race-unsafe (it could overwrite a
 * concurrent {@code PENDING -> BOUND} from the binder).
 *
 * <p>The cleanup task is enqueued through
 * {@link StorageCleanupTaskService#enqueueNewObjectCleanupInCurrentTransaction},
 * which is {@code @Transactional(MANDATORY)} and uses the same internal
 * conditional-insert repository, so it commits with the row transition
 * or rolls back with it. There is no second transaction opened from
 * inside this method.
 *
 * <h2>Fresh "now" inside the transaction</h2>
 * <p>The clock is sampled INSIDE each {@code REQUIRES_NEW} method,
 * not supplied by the caller. This guarantees the
 * {@code expires_at > :now} / {@code expires_at <= :now} predicate is
 * evaluated against the time the transition actually runs, not a
 * timestamp that might have been taken before a remote Supabase
 * roundtrip.
 *
 * <h2>No retry on cleanup failure</h2>
 * <p>If the conditional insert raises any runtime exception, the
 * surrounding transaction rolls back and the pending-row transition
 * is reverted. That guarantees the database can never observe
 * "PENDING row flipped to CANCELED without a corresponding cleanup
 * task" or "task inserted for a row that was not transitioned".
 *
 * <h2>Idempotency</h2>
 * <p>If the pending row is no longer in {@code PENDING} state, both
 * methods become no-ops: the conditional UPDATE matches zero rows,
 * the empty result is returned, and no cleanup task is inserted. The
 * conditional insert in
 * {@link StorageCleanupTaskService#enqueueNewObjectCleanupInCurrentTransaction}
 * will also short-circuit when an active equivalent task already
 * exists. Therefore, replayed requests cannot create duplicate
 * active tasks.
 */
@Service
public class PendingUploadFailureServiceImpl implements PendingUploadFailureService {

    private static final Logger log =
            LoggerFactory.getLogger(PendingUploadFailureServiceImpl.class);

    private final PendingStorageUploadRepository pendingUploadRepository;
    private final StorageCleanupTaskService storageCleanupTaskService;
    private final Clock clock;

    public PendingUploadFailureServiceImpl(
            PendingStorageUploadRepository pendingUploadRepository,
            StorageCleanupTaskService storageCleanupTaskService,
            Clock clock) {
        this.pendingUploadRepository = pendingUploadRepository;
        this.storageCleanupTaskService = storageCleanupTaskService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome cancelAndEnqueueVerificationFailure(
            UUID uploadId,
            UUID currentUserId,
            StorageCleanupReason reason,
            LocalDateTime now,
            String safeFailureCode) {

        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId must not be null");
        }
        if (currentUserId == null) {
            throw new IllegalArgumentException("currentUserId must not be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        // 'now' is the caller-supplied value, kept for API stability
        // and logging; the transition itself samples its own
        // authoritative 'failureNow' from the application clock.
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        LocalDateTime failureNow = LocalDateTime.now(clock);

        Optional<PendingUploadTransitionTarget> transitioned =
                pendingUploadRepository.cancelPendingForVerificationFailure(
                        uploadId, currentUserId, failureNow);

        if (transitioned.isEmpty()) {
            // The conditional UPDATE matched zero rows. The row is no
            // longer in (PENDING, not-expired) state. The exact
            // sub-state (BOUND, CANCELED, CLEANING, EXPIRED, or
            // simply PENDING-but-expired) is not visible to us here;
            // for a verification-failure the only safe action is
            // "do nothing":
            //   - BOUND: must not be reverted; the binder has already
            //     created a Document and may have wired it into
            //     DocumentFile. Enqueuing a cleanup here would race
            //     the Document that's already using the object.
            //   - CANCELED: idempotent — do not create a duplicate
            //     task.
            //   - CLEANING / EXPIRED: a previous failure path already
            //     enqueued cleanup; re-enqueueing would duplicate
            //     active work.
            //   - PENDING-but-expired: this method must not steal
            //     expiry work; the orchestrator's snapshot step is
            //     the right place to detect that and route to
            //     transitionExpiredAndEnqueueCleanup.
            log.debug(
                    "cancelAndEnqueueVerificationFailure: no row matched"
                            + " (already bound / canceled / cleaning /"
                            + " expired, or expired concurrently). code={}",
                    safeFailureCode);
            return Outcome.ALREADY_NOT_PENDING;
        }

        PendingUploadTransitionTarget target = transitioned.get();

        // The cleanup task MUST commit in the same transaction as
        // the row transition. The service is @Transactional(MANDATORY),
        // so any exception here rolls back the row flip too. The
        // bucket and path come from the OUTPUT clause of the same
        // UPDATE that flipped the row — never from a pre-transaction
        // snapshot.
        StorageCleanupTask task = storageCleanupTaskService
                .enqueueNewObjectCleanupInCurrentTransaction(
                        target.storageBucket(),
                        target.storagePath(),
                        reason,
                        uploadId,
                        null);

        log.info(
                "Pending upload {} canceled by {} (taskId={}, reason={},"
                        + " code={})",
                uploadId, currentUserId,
                task != null ? task.getId() : null,
                reason, safeFailureCode);

        return Outcome.CANCELED;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExpireOutcome transitionExpiredAndEnqueueCleanup(
            UUID uploadId,
            UUID currentUserId,
            LocalDateTime now,
            String safeFailureCode) {

        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId must not be null");
        }
        if (currentUserId == null) {
            throw new IllegalArgumentException("currentUserId must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        LocalDateTime failureNow = LocalDateTime.now(clock);

        Optional<PendingUploadTransitionTarget> transitioned =
                pendingUploadRepository.claimExpiredPendingForCleanup(
                        uploadId, currentUserId, failureNow);

        if (transitioned.isEmpty()) {
            // Conditional UPDATE matched zero rows. The row is either
            // not yet expired (still bindable) or already in a
            // terminal state. Either way, this method does not act.
            //   - PENDING-but-not-yet-expired: a concurrent bind
            //     attempt or a verification-failure attempt is
            //     responsible for this row; do not steal the
            //     transition.
            //   - CLEANING / EXPIRED: a previous expiry sweep
            //     already enqueued cleanup.
            //   - CANCELED / BOUND: the row has already been
            //     resolved by another path; do nothing.
            log.debug(
                    "transitionExpiredAndEnqueueCleanup: no row matched"
                            + " (still bindable, or already terminal)."
                            + " code={}",
                    safeFailureCode);
            return ExpireOutcome.ALREADY_TERMINAL;
        }

        PendingUploadTransitionTarget target = transitioned.get();

        // The OUTPUT-bucket/path are the authoritative target for
        // the cleanup task. They commit in the same transaction as
        // the row transition.
        StorageCleanupTask task = storageCleanupTaskService
                .enqueueNewObjectCleanupInCurrentTransaction(
                        target.storageBucket(),
                        target.storagePath(),
                        StorageCleanupReason.EXPIRED_PENDING_UPLOAD,
                        uploadId,
                        null);

        log.info(
                "Pending upload {} expired; transitioned PENDING->CLEANING"
                        + " (taskId={}, code={})",
                uploadId,
                task != null ? task.getId() : null,
                safeFailureCode);

        return ExpireOutcome.CLEANING_SCHEDULED;
    }
}
