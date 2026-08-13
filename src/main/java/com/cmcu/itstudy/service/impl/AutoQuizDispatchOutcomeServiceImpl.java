package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.AutoQuizDispatchProperties;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.repository.custom.SafeArtifactLastError;
import com.cmcu.itstudy.service.contract.AutoQuizDispatchOutcomeService;
import com.cmcu.itstudy.service.contract.AutoQuizN8nDispatchClient.DispatchOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring-service implementation of
 * {@link AutoQuizDispatchOutcomeService}.
 *
 * <p>Lives in its <strong>own Spring bean</strong> precisely to avoid
 * the same-bean self-invocation trap: when
 * {@link com.cmcu.itstudy.service.impl.AutoQuizDispatcherServiceImpl#runCycle()}
 * reaches {@code processOne()} which calls
 * {@code outcomeService.apply(...)}, the call always crosses a Spring
 * proxy. The {@code @Transactional(REQUIRES_NEW)} on this method is
 * therefore honoured and each outcome-update {@code @Modifying}
 * repository call runs inside a real database transaction.</p>
 *
 * <h2>Concurrency contract</h2>
 * <ul>
 *   <li>The lease token is echoed in the {@code WHERE} clause so a
 *       CANCELLED race or lease rotation cannot be overwritten by a
 *       late HTTP response.</li>
 *   <li>On {@code affectedRows == 0} the lease has been invalidated
 *       by another writer (CANCELLED race) and the method returns
 *       {@code SKIPPED_NOT_QUEUED} without resurrecting the row.</li>
 * </ul>
 *
 * <h2>Backoff</h2>
 * <p>The retry schedule is the same bounded
 * {@code +15s, +30s, +60s, +120s…} capped at {@code +5m} previously
 * owned by the dispatcher service. It is local to this class because
 * the persisted {@code QuizGeneration.nextAttemptAt} formula is not
 * used by the dispatcher.</p>
 */
@Service
public class AutoQuizDispatchOutcomeServiceImpl
        implements AutoQuizDispatchOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(
            AutoQuizDispatchOutcomeServiceImpl.class);

    /**
     * Operational retry schedule. Each entry is the wait time
     * BEFORE the next dispatch attempt (i.e. for the row whose
     * {@code attempts} column has just been incremented to the
     * matching index).
     */
    static final Duration[] RETRY_SCHEDULE = new Duration[]{
            Duration.ofSeconds(15),
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            Duration.ofSeconds(120),
            Duration.ofSeconds(240),
            Duration.ofSeconds(300)
    };

    /**
     * Operational error code prefix for terminal FAILED transitions.
     */
    static final String TERMINAL_CODE_PREFIX = "AUTOQUIZ_DISPATCH_FAILED";

    private final AutoQuizDispatchProperties properties;
    private final QuizGenerationRepository repository;

    public AutoQuizDispatchOutcomeServiceImpl(
            AutoQuizDispatchProperties properties,
            QuizGenerationRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    /**
     * Translate an HTTP {@link DispatchOutcome} into the database
     * state transition for {@code row}.
     *
     * <p>Runs in {@code REQUIRES_NEW} so the lease is cleared
     * atomically with the state change. The HTTP call has already
     * returned by the time we land here; no HTTP traffic is on the
     * stack while the transaction is open.</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CycleDecision apply(QuizGeneration row, UUID token,
                                DispatchOutcome outcome, LocalDateTime now) {
        UUID generationId = row.getId();
        int attemptsAfter = row.getAttempts() == null
                ? 0 : row.getAttempts();

        if (outcome.isSuccess()) {
            int updated = repository.markDispatchedToProcessing(
                    generationId, token, now);
            if (updated != 1) {
                log.info(
                        "Auto Quiz dispatcher: 2xx response for "
                                + "generationId={} but row no longer "
                                + "matches the lease; ignoring "
                                + "(CANCELLED race or stale token).",
                        generationId);
                return CycleDecision.SKIPPED_NOT_QUEUED;
            }
            log.info(
                    "Auto Quiz dispatcher: generationId={} → PROCESSING "
                            + "(attempts={}, httpStatus={})",
                    generationId, attemptsAfter + 1,
                    outcome.httpStatus());
            return CycleDecision.DISPATCHED_PROCESSING;
        }

        // Failure path. Determine whether to retry or fail
        // terminally based on attempts + 1 (the increment is
        // performed atomically by the SQL UPDATE).
        int attemptsAfterIncrement = attemptsAfter + 1;
        int maxAttempts = properties.getMaxAttempts();
        if (attemptsAfterIncrement >= maxAttempts) {
            String code = boundedTerminalCode(outcome);
            int updated = repository.releaseLeaseToFailed(
                    generationId, token, code, now);
            if (updated != 1) {
                log.info(
                        "Auto Quiz dispatcher: FAILED transition for "
                                + "generationId={} but row no longer "
                                + "matches the lease; ignoring "
                                + "(CANCELLED race).",
                        generationId);
                return CycleDecision.SKIPPED_NOT_QUEUED;
            }
            log.warn(
                    "Auto Quiz dispatcher: generationId={} → FAILED "
                            + "(attempts={}, maxAttempts={}, "
                            + "errorCode={})",
                    generationId, attemptsAfterIncrement, maxAttempts,
                    code);
            return CycleDecision.DISPATCHED_FAILED;
        }

        LocalDateTime nextAttempt = now.plus(
                retryDelayForAttempt(attemptsAfter));
        String retryCode = boundedRetryCode(outcome);
        int updated = repository.releaseLeaseForRetry(
                generationId, token, retryCode, nextAttempt, now);
        if (updated != 1) {
            log.info(
                    "Auto Quiz dispatcher: retry transition for "
                            + "generationId={} but row no longer "
                            + "matches the lease; ignoring "
                            + "(CANCELLED race).",
                    generationId);
            return CycleDecision.SKIPPED_NOT_QUEUED;
        }
        log.info(
                "Auto Quiz dispatcher: generationId={} retry scheduled "
                        + "(attempts={}, nextAttemptAt={}, errorCode={})",
                generationId, attemptsAfterIncrement,
                nextAttempt, retryCode);
        return CycleDecision.DISPATCHED_RETRY;
    }

    /**
     * Compute the retry delay BEFORE the next dispatch attempt for
     * a row whose {@code attempts} column has just been incremented
     * to {@code attemptsAfter}.
     */
    static Duration retryDelayForAttempt(int attemptsAfter) {
        int idx = Math.min(
                Math.max(attemptsAfter - 1, 0),
                RETRY_SCHEDULE.length - 1);
        return RETRY_SCHEDULE[idx];
    }

    static String boundedRetryCode(DispatchOutcome outcome) {
        String raw = outcome.errorCode();
        String safe = SafeArtifactLastError.sanitize(raw, 80);
        return safe == null ? "AUTOQUIZ_DISPATCH_RETRY" : safe;
    }

    static String boundedTerminalCode(DispatchOutcome outcome) {
        String raw = TERMINAL_CODE_PREFIX + ":"
                + (outcome.errorCode() == null
                        ? "UNKNOWN" : outcome.errorCode());
        String safe = SafeArtifactLastError.sanitize(raw, 120);
        return safe == null
                ? TERMINAL_CODE_PREFIX + ":UNKNOWN" : safe;
    }
}