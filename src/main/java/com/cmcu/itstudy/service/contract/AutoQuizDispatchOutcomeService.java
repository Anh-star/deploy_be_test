package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.autoquiz.AutoQuizDispatchPayloadDto;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.service.contract.AutoQuizN8nDispatchClient.DispatchOutcome;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outcome-update contract for the Auto Quiz dispatcher.
 *
 * <p>Lives in its own Spring bean so each
 * {@code @Modifying} outcome-update repository call runs inside a
 * real Spring {@code @Transactional(REQUIRES_NEW)} proxy. A
 * same-bean self-invocation from {@code processOne()} would otherwise
 * bypass the proxy and the {@code @Modifying} query would throw
 * {@code TransactionRequiredException}.</p>
 *
 * <p>Each branch of the dispatch handshake
 * (PROCESSING success, retry, terminal FAILED) is mapped to its own
 * short-lived REQUIRES_NEW transaction so:</p>
 * <ul>
 *   <li>the lease is cleared atomically with the state change,</li>
 *   <li>the HTTP call is NOT pulled into any DB transaction (it has
 *       already returned by the time we land here),</li>
 *   <li>a CANCELLED row is never resurrected because the
 *       {@code WHERE} clause carries the expected
 *       {@code dispatchToken}.</li>
 * </ul>
 */
public interface AutoQuizDispatchOutcomeService {

    /**
     * The per-row decision emitted by
     * {@link com.cmcu.itstudy.service.impl.AutoQuizDispatcherServiceImpl}.
     * Exposed publicly so {@link AutoQuizDispatchOutcomeService} can
     * return the same enumeration.
     */
    enum CycleDecision {
        DISPATCHED_PROCESSING,
        DISPATCHED_RETRY,
        DISPATCHED_FAILED,
        SKIPPED_NOT_FOUND,
        SKIPPED_NOT_QUEUED,
        SKIPPED_LOAD_FAILED
    }

    /**
     * Translate an HTTP {@link DispatchOutcome} into the database
     * state transition for {@code row}.
     *
     * <p>Runs in {@code REQUIRES_NEW} so the lease is cleared
     * atomically with the state change. The transaction opens for
     * the {@code @Modifying} repository call and commits as soon as
     * it returns — no HTTP traffic is on the stack while the
     * transaction is open.</p>
     *
     * @param row      the loaded {@link QuizGeneration} snapshot used
     *                 to compute the next attempt / error code
     * @param token    the lease token the worker holds; echoed in the
     *                 {@code WHERE} clause to prevent CANCELLED races
     * @param outcome  the HTTP dispatch outcome
     * @param now      caller-supplied cycle timestamp
     * @return the per-row decision the dispatcher aggregates
     */
    CycleDecision apply(QuizGeneration row, UUID token,
                        DispatchOutcome outcome, LocalDateTime now);
}