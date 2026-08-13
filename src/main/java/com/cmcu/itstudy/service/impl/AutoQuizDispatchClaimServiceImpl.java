package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.service.contract.AutoQuizDispatchClaimService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring-service implementation of
 * {@link AutoQuizDispatchClaimService}.
 *
 * <p>Lives in its <strong>own Spring bean</strong> precisely to avoid
 * the same-bean self-invocation trap: when
 * {@link com.cmcu.itstudy.service.contract.AutoQuizDispatcherService#runCycle()}
 * reaches {@code processOne()} which calls
 * {@code claimService.claim(...)}, the call always crosses a Spring
 * proxy. The {@code @Transactional(REQUIRES_NEW)} on this method is
 * therefore honoured and a <em>real</em> database transaction is
 * opened for the {@code @Modifying} claim query.</p>
 *
 * <h2>Failure semantics</h2>
 * <ul>
 *   <li>{@code 0 affected rows} → no transaction is opened (Spring
 *       never commits an empty REQUIRES_NEW) and {@code false} is
 *       returned. The caller treats this as a SKIPPED decision.</li>
 *   <li>{@code 1 affected row} → transaction commits and the lease
 *       is durable across JVM crashes.</li>
 *   <li>{@code RuntimeException} → transaction rolls back and the
 *       exception is propagated to the caller.</li>
 * </ul>
 */
@Service
public class AutoQuizDispatchClaimServiceImpl
        implements AutoQuizDispatchClaimService {

    private static final Logger log = LoggerFactory.getLogger(
            AutoQuizDispatchClaimServiceImpl.class);

    private final QuizGenerationRepository repository;

    public AutoQuizDispatchClaimServiceImpl(QuizGenerationRepository repository) {
        this.repository = repository;
    }

    /**
     * Atomically lease one QUEUED row for the dispatcher. Runs in its
     * own {@code REQUIRES_NEW} transaction so the lease commits
     * BEFORE any HTTP call is made.
     *
     * <p>If the repository call returns {@code 0}, the row was either
     * missing, no longer {@code QUEUED}, or already leased by another
     * worker. The Spring transaction simply commits empty and the
     * caller treats the {@code false} return value as
     * {@code SKIPPED_NOT_QUEUED}.</p>
     *
     * @param generationId id of the row to claim
     * @param token        fresh lease token for the worker
     * @param now          cycle timestamp used for {@code dispatchTokenIssuedAt}
     * @return {@code true} if the row was claimed; {@code false}
     *         otherwise
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(UUID generationId, UUID token, LocalDateTime now) {
        int updated = repository.claimQueuedForDispatch(
                generationId, token, now);
        if (updated != 1) {
            log.debug(
                    "Auto Quiz dispatcher claim lost for "
                            + "generationId={} affectedRows={}",
                    generationId, updated);
            return false;
        }
        return true;
    }
}