package com.cmcu.itstudy.service.contract;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Atomic dispatch-claim contract for the Auto Quiz pipeline.
 *
 * <p>Lives in its own Spring bean so that
 * {@link #claim(UUID, UUID, LocalDateTime)} always executes inside a
 * real Spring {@code @Transactional(REQUIRES_NEW)} proxy — avoiding the
 * same-bean self-invocation trap where {@code @Transactional} is
 * bypassed by a call from {@code runCycle() → processOne() → claim}.</p>
 *
 * <p>The lease acquired here is durable across JVM crashes because
 * the transaction commits BEFORE the HTTP call is made.</p>
 */
public interface AutoQuizDispatchClaimService {

    /**
     * Atomically transition one {@code QUEUED} row to a leased state
     * owned by this worker.
     *
     * <p>The SQL contract (mirrored on
     * {@code QuizGenerationRepository#claimQueuedForDispatch}):</p>
     * <ul>
     *   <li>SET: {@code dispatchToken = :token},
     *       {@code dispatchTokenIssuedAt = :now},
     *       {@code updatedAt = :now},
     *       {@code lastError = NULL}.</li>
     *   <li>WHERE: {@code id = :generationId}
     *       AND {@code status = QUEUED}
     *       AND {@code dispatchToken IS NULL}.</li>
     * </ul>
     *
     * <p>The {@code attempts} column is <strong>not</strong> incremented
     * here — the claim itself is not a failed attempt.</p>
     *
     * @param generationId id of the row to claim
     * @param token        fresh lease token this worker will hold
     * @param now          caller-supplied cycle timestamp
     * @return {@code true} if exactly one row was claimed;
     *         {@code false} if the row was missing, in a different
     *         status, or already leased by another worker.
     */
    boolean claim(UUID generationId, UUID token, LocalDateTime now);
}