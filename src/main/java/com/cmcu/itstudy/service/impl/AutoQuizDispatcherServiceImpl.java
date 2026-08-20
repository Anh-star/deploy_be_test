package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.AutoQuizDispatchProperties;
import com.cmcu.itstudy.dto.autoquiz.AutoQuizDispatchPayloadDto;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.repository.custom.SafeArtifactLastError;
import com.cmcu.itstudy.service.contract.AutoQuizDispatcherService;
import com.cmcu.itstudy.service.contract.AutoQuizDispatchClaimService;
import com.cmcu.itstudy.service.contract.AutoQuizDispatchLeaseService;
import com.cmcu.itstudy.service.contract.AutoQuizDispatchOutcomeService;
import com.cmcu.itstudy.service.contract.AutoQuizN8nDispatchClient;
import com.cmcu.itstudy.service.contract.AutoQuizN8nDispatchClient.DispatchOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link AutoQuizDispatcherService} implementation.
 *
 * <h2>State machine</h2>
 * <pre>
 *   QUEUED (no lease, due now)
 *       │
 *       ▼  claim (REQUIRES_NEW commit)
 *   QUEUED + dispatchToken lease
 *       │
 *       ▼  HTTP POST OUTSIDE the DB transaction
 *   ┌──────────┬───────────────┐
 *   │ 2xx      │ non-2xx / err │
 *   ▼          ▼               ▼
 *   PROCESSING   retry (< max)  FAILED (>= max)
 * </pre>
 *
 * <h2>Concurrency contract</h2>
 * <ul>
 *   <li>The claim SQL is the cross-instance atomic owner: two
 *       workers competing for the same row will see exactly one
 *       {@code affectedRows == 1}.</li>
 *   <li>The HTTP call is performed OUTSIDE any database
 *       transaction (see
 *       {@link #processOne(UUID, UUID, LocalDateTime)}). The lease
 *       is durable across JVM crashes because the claim
 *       transaction commits before the HTTP call.</li>
 *   <li>The completion UPDATEs all carry the expected
 *       {@code dispatchToken} in the {@code WHERE} clause, so a
 *       CANCELLED race (or a lease rotation) cannot be overwritten
 *       by a late HTTP response.</li>
 * </ul>
 *
 * <h2>Backoff</h2>
 * <p>The retry schedule is bounded: {@code +15s, +30s, +60s, +120s…}
 * capped at {@code +5m}. The schedule is local to this service —
 * it is NOT driven by the persisted
 * {@code QuizGeneration.nextAttemptAt} formula because the latter
 * is reserved for the natural attempt-at model used elsewhere in
 * the project.</p>
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>No credentials, JWT, signed URLs or Supabase keys are
 *       ever written to the JSON payload.</li>
 *   <li>{@code lastError} is sanitised through the existing
 *       {@link SafeArtifactLastError} helper so a leaked stack
 *       trace cannot land in the database.</li>
 *   <li>The webhook URL is never logged in full; only
 *       {@link AutoQuizDispatchProperties#safeWebhookSummary()}
 *       is emitted.</li>
 * </ul>
 */
@Service
public class AutoQuizDispatcherServiceImpl implements AutoQuizDispatcherService {

    private static final Logger log =
            LoggerFactory.getLogger(AutoQuizDispatcherServiceImpl.class);

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
    private final AutoQuizN8nDispatchClient n8nClient;
    private final AutoQuizDispatchLeaseService leaseService;
    private final AutoQuizDispatchClaimService claimService;
    private final AutoQuizDispatchOutcomeService outcomeService;
    private final Clock clock;

    /**
     * In-memory cache of the {@code dispatchToken} leases held by
     * THIS process. Not authoritative — the database is — but it
     * lets the completion UPDATEs include the expected token in
     * the {@code WHERE} clause so a CANCELLED row can never be
     * resurrected.
     *
     * <p>{@link java.util.HashMap} access is sufficient because the
     * dispatch path is single-threaded per row: claim → HTTP →
     * transition runs sequentially for a given generation id.</p>
     */
    private final Map<UUID, UUID> leaseByGeneration = new HashMap<>();

    public AutoQuizDispatcherServiceImpl(
            AutoQuizDispatchProperties properties,
            QuizGenerationRepository repository,
            AutoQuizN8nDispatchClient n8nClient,
            AutoQuizDispatchLeaseService leaseService,
            AutoQuizDispatchClaimService claimService,
            AutoQuizDispatchOutcomeService outcomeService,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.n8nClient = Objects.requireNonNull(n8nClient, "n8nClient");
        this.leaseService = Objects.requireNonNull(leaseService, "leaseService");
        this.claimService = Objects.requireNonNull(claimService, "claimService");
        this.outcomeService = Objects.requireNonNull(outcomeService, "outcomeService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public UUID currentLeaseTokenForTest(UUID generationId) {
        return leaseByGeneration.get(generationId);
    }

    @Override
    public CycleOutcome runCycle() {
        if (!properties.isEnabled()) {
            log.debug("Auto Quiz dispatcher disabled; skipping cycle");
            return CycleOutcome.noop();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int batchSize = properties.getBatchSize();

        // Crash-recovery: clear any QUEUED row whose dispatch
        // lease is older than leaseTimeout (or whose issuedAt is
        // null — orphan tokens from corrupted / historical rows).
        // Without this step a JVM crash between claim commit and
        // HTTP outcome would leave a stuck row that no future
        // claim can win (the claim SQL requires dispatchToken
        // IS NULL).
        //
        // CALLED THROUGH A SEPARATE SPRING BEAN so the call always
        // crosses a Spring proxy and the @Transactional(REQUIRES_NEW)
        // on the lease service is honoured (same-bean self-invocation
        // bypasses the proxy and would leave the bulk UPDATE without
        // a transaction).
        int released;
        try {
            released = leaseService.releaseStaleLeases(now);
        } catch (RuntimeException e) {
            log.warn(
                    "Auto Quiz dispatcher stale-lease release failed; "
                            + "continuing without recovery this cycle",
                    e);
            released = 0;
        }

        List<UUID> candidates;
        try {
            candidates = repository.findQueuedDispatchCandidates(
                    batchSize, now);
        } catch (RuntimeException e) {
            log.warn("Auto Quiz dispatcher candidate lookup failed", e);
            return CycleOutcome.noop();
        }
        if (candidates.isEmpty()) {
            log.debug("Auto Quiz dispatcher cycle idle (no candidates)");
            return CycleOutcome.noop();
        }
        log.info(
                "Auto Quiz dispatcher cycle started now={} candidates={} "
                        + "batchSize={} staleLeasesReleased={}",
                now, candidates.size(), batchSize, released);

        int claimed = 0;
        int dispatched = 0;
        int processing = 0;
        int retry = 0;
        int failed = 0;
        int skipped = 0;

        for (UUID generationId : candidates) {
            if (Thread.currentThread().isInterrupted()) {
                skipped++;
                break;
            }
            try {
                AutoQuizDispatchOutcomeService.CycleDecision decision =
                        processOne(generationId, null, now);
                if (decision == AutoQuizDispatchOutcomeService.CycleDecision
                        .DISPATCHED_PROCESSING
                        || decision == AutoQuizDispatchOutcomeService.CycleDecision
                        .DISPATCHED_RETRY
                        || decision == AutoQuizDispatchOutcomeService.CycleDecision
                        .DISPATCHED_FAILED) {
                    claimed++;
                }
                switch (decision) {
                    case DISPATCHED_PROCESSING -> {
                        dispatched++;
                        processing++;
                    }
                    case DISPATCHED_RETRY -> {
                        dispatched++;
                        retry++;
                    }
                    case DISPATCHED_FAILED -> {
                        dispatched++;
                        failed++;
                    }
                    case SKIPPED_NOT_FOUND, SKIPPED_NOT_QUEUED,
                            SKIPPED_LOAD_FAILED -> {
                        skipped++;
                    }
                    default -> skipped++;
                }
            } catch (RuntimeException e) {
                // Defensive: a single bad row must NEVER sink the
                // whole cycle.
                log.warn(
                        "Auto Quiz dispatcher cycle swallowed unexpected "
                                + "error for generationId={}",
                        generationId, e);
                skipped++;
            }
        }
        CycleOutcome outcome = new CycleOutcome(
                candidates.size(), claimed, dispatched, processing,
                retry, failed, skipped);
        log.info("Auto Quiz dispatcher cycle finished {}", outcome);
        return outcome;
    }

    /**
     * Per-row processing. Returns the per-row decision so the
     * outer cycle can aggregate counters.
    CycleOutcome outcome = new CycleOutcome(
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Load the row (read-only) to obtain the parent ids and
     *       {@code requestedQuestionCount}.</li>
     *   <li>Compute a fresh lease token and call
     *       {@code claimService.claim(...)}. On {@code false}
     *       return {@code SKIPPED_NOT_QUEUED} (race with another
     *       worker or cancellation).</li>
     *   <li>Build the payload and POST OUTSIDE the database
     *       transaction.</li>
     *   <li>Map the outcome onto a completion UPDATE through
     *       {@code outcomeService.apply(...)}.</li>
     * </ol>
     *
     * <p>The HTTP call is performed outside any Spring-managed
     * transaction. The two completion branches are individually
     * wrapped in {@code REQUIRES_NEW} on the outcome service so
     * the lease is always cleared atomically with the state
     * change.</p>
     */
    AutoQuizDispatchOutcomeService.CycleDecision processOne(
            UUID generationId, UUID presetToken, LocalDateTime now) {
        Optional<QuizGeneration> loaded =
                repository.findById(generationId);
        if (loaded.isEmpty()) {
            log.warn(
                    "Auto Quiz dispatcher: generationId={} not found",
                    generationId);
            return AutoQuizDispatchOutcomeService.CycleDecision.SKIPPED_NOT_FOUND;
        }
        QuizGeneration row = loaded.get();
        if (row.getStatus() != QuizGenerationStatus.QUEUED) {
            log.debug(
                    "Auto Quiz dispatcher: generationId={} not QUEUED "
                            + "(status={})",
                    generationId, row.getStatus());
            return AutoQuizDispatchOutcomeService.CycleDecision.SKIPPED_NOT_QUEUED;
        }

        UUID documentId = row.getDocument() == null
                ? null : row.getDocument().getId();
        UUID documentFileId = row.getDocumentFile() == null
                ? null : row.getDocumentFile().getId();
        Integer requestedQuestionCount = row.getRequestedQuestionCount();
        String focusTopic = row.getFocusTopic();

        // Lease token is computed BEFORE the claim so the
        // completion UPDATEs can echo it back in the WHERE clause.
        UUID token = presetToken != null
                ? presetToken : UUID.randomUUID();

        // CALLED THROUGH A SEPARATE SPRING BEAN so the call always
        // crosses a Spring proxy and the @Transactional(REQUIRES_NEW)
        // on the claim service is honoured (same-bean self-invocation
        // would leave the bulk UPDATE without a transaction and Hibernate
        // would throw TransactionRequiredException for the @Modifying
        // query).
        if (!claimService.claim(generationId, token, now)) {
            log.debug(
                    "Auto Quiz dispatcher: generationId={} claim lost "
                            + "(race or cancellation)",
                    generationId);
            return AutoQuizDispatchOutcomeService.CycleDecision.SKIPPED_NOT_QUEUED;
        }
        leaseByGeneration.put(generationId, token);

        AutoQuizDispatchPayloadDto payload = new AutoQuizDispatchPayloadDto(
                generationId, documentId, documentFileId,
                requestedQuestionCount, focusTopic, token);

        // HTTP MUST stay OUTSIDE any DB transaction.  The claim is
        // already committed (REQUIRES_NEW), so a transport error here
        // cannot roll back the lease — the outcome service handles
        // the lease release in its own REQUIRES_NEW transaction below.
        DispatchOutcome outcome;
        try {
            outcome = n8nClient.dispatch(payload);
        } catch (RuntimeException transport) {
            // Defensive: the client already maps the known
            // failures onto DispatchOutcome; this branch catches
            // anything that escaped the switch.
            log.warn(
                    "Auto Quiz dispatcher: n8n client threw for "
                            + "generationId={}",
                    generationId, transport);
            outcome = DispatchOutcome.transientFailure(
                    AutoQuizN8nDispatchClientImpl.CODE_UNEXPECTED);
        }

        // Outcome service runs the state transition in its own
        // REQUIRES_NEW transaction. Always called through the
        // injected bean reference so the proxy fires.
        AutoQuizDispatchOutcomeService.CycleDecision decision =
                outcomeService.apply(row, token, outcome, now);
        // The dispatcher-side lease cache mirrors the DB state.
        if (decision == AutoQuizDispatchOutcomeService.CycleDecision
                .DISPATCHED_PROCESSING
                || decision == AutoQuizDispatchOutcomeService.CycleDecision
                .DISPATCHED_RETRY
                || decision == AutoQuizDispatchOutcomeService.CycleDecision
                .DISPATCHED_FAILED) {
            leaseByGeneration.remove(generationId);
        }
        return decision;
    }

    /**
     * Compute the retry delay BEFORE the next dispatch attempt for
     * a row whose {@code attempts} column has just been incremented
     * to {@code attemptsAfter}.
     *
     * <p>The schedule is bounded so a long-running outage cannot
     * back the queue up indefinitely.</p>
     *
     * <p>Retained here so any test or downstream caller that
     * referenced the dispatcher-side helper still compiles. The
     * production retry logic now lives in
     * {@link AutoQuizDispatchOutcomeServiceImpl}.</p>
     */
    static Duration retryDelayForAttempt(int attemptsAfter) {
        int idx = Math.min(
                Math.max(attemptsAfter - 1, 0),
                RETRY_SCHEDULE.length - 1);
        return RETRY_SCHEDULE[idx];
    }
}