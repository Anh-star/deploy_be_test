package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.AutoQuizDispatchProperties;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.service.contract.AutoQuizDispatchLeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Spring-service implementation of
 * {@link AutoQuizDispatchLeaseService}.
 *
 * <p>Lives in its <strong>own Spring bean</strong> precisely to avoid
 * the same-bean self-invocation trap: when
 * {@link com.cmcu.itstudy.service.contract.AutoQuizDispatcherService#runCycle()}
 * calls {@code leaseService.releaseStaleLeases()}, the call always
 * crosses a Spring proxy, so the {@code @Transactional(REQUIRES_NEW)}
 * on this method is honoured and a <em>real</em> database transaction
 * is opened for the bulk {@code UPDATE}.</p>
 *
 * <p>Design invariants:</p>
 * <ul>
 *   <li>The call is <strong>always the first step</strong> in
 *       {@code runCycle()}, before candidate lookup.</li>
 *   <li>The method is <strong>best-effort</strong>: if the DB call
 *       throws, the exception is caught, a warning is logged, and
 *       {@code 0} is returned so the cycle continues.</li>
 *   <li>No HTTP call is performed inside this transaction.</li>
 *   <li>The {@code SET} clause touches only {@code dispatchToken},
 *       {@code dispatchTokenIssuedAt}, and {@code updatedAt} — all
 *       other columns (including {@code attempts},
 *       {@code lastAttemptAt}, {@code nextAttemptAt},
 *       {@code lastError}) are preserved.</li>
 * </ul>
 */
@Service
public class AutoQuizDispatchLeaseServiceImpl
        implements AutoQuizDispatchLeaseService {

    private static final Logger log = LoggerFactory.getLogger(
            AutoQuizDispatchLeaseServiceImpl.class);

    private final AutoQuizDispatchProperties properties;
    private final QuizGenerationRepository repository;

    public AutoQuizDispatchLeaseServiceImpl(
            AutoQuizDispatchProperties properties,
            QuizGenerationRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    /**
     * Releases stale {@code QUEUED} dispatch leases.
     *
     * <p>Runs in its own {@code REQUIRES_NEW} transaction, so the
     * bulk {@code UPDATE} is committed independently of any outer
     * transactional context (or lack thereof) in the caller.</p>
     *
     * @param now caller-supplied cycle timestamp; used to compute
     *           {@code staleBefore = now - leaseTimeout} and to stamp
     *           {@code updatedAt}
     * @return number of stale leases released (0 to N)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int releaseStaleLeases(LocalDateTime now) {
        LocalDateTime staleBefore = now.minus(properties.getLeaseTimeout());
        int released;
        try {
            released = repository.releaseStaleDispatchLeases(
                    staleBefore, now);
        } catch (RuntimeException e) {
            log.warn(
                    "Auto Quiz dispatcher stale-lease release failed; "
                            + "continuing without recovery this cycle",
                    e);
            return 0;
        }
        if (released > 0) {
            log.info(
                    "Auto Quiz dispatcher released {} stale dispatch "
                            + "lease(s) older than {} (leaseTimeout={})",
                    released, staleBefore, properties.getLeaseTimeout());
        }
        return released;
    }
}
