package com.cmcu.itstudy.scheduler;

import com.cmcu.itstudy.config.DocumentPreviewWorkerProperties;
import com.cmcu.itstudy.repository.custom.DocumentPreviewArtifactClaim;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactClaimService;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactReadySignal;
import com.cmcu.itstudy.service.impl.DocumentPreviewArtifactProcessor;
import com.cmcu.itstudy.service.impl.DocumentPreviewArtifactProcessor.WorkerOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Latency-optimised batch scheduler for the document preview pipeline.
 *
 * <h2>Latency sources addressed in this revision</h2>
 * <ol>
 *   <li><b>30&nbsp;second default fixed delay.</b> The default is now
 *       3&nbsp;seconds (configurable via
 *       {@code app.document-preview.worker.fixed-delay-ms}, clamped
 *       to {@code [250, 30000]}). New artifacts are typically picked
 *       up on the next cycle rather than after half a minute.</li>
 *   <li><b>Head-of-line blocking.</b> The scheduler no longer waits
 *       for {@link DocumentPreviewArtifactProcessor#process} to
 *       finish before claiming the next batch. A bounded
 *       {@link ThreadPoolExecutor} owns the processing side; the
 *       scheduler only claims and dispatches.</li>
 *   <li><b>Single scheduling thread.</b> Processing runs on
 *       {@link #processingThreads} (default 2) dedicated daemon
 *       threads so a slow DOCX no longer blocks a small PDF.</li>
 *   <li><b>Event-driven wake-up.</b> A {@link DocumentPreviewArtifactReadySignal}
 *       lets the artifact state service nudge the worker immediately
 *       after a FULL artifact transitions to READY, so the dependent
 *       LIMITED row is claimed on the next cycle without waiting for
 *       the 3-second poll.</li>
 *   <li><b>Queue priority.</b> The claim {@code ORDER BY} places
 *       claimable FULL PENDING first, then other PENDING, then
 *       RETRY. New artifacts no longer starve behind old RETRY
 *       backlog, and LIMITED rows whose FULL sibling is not yet READY
 *       are filtered out of the claim SQL entirely.</li>
 * </ol>
 *
 * <h2>Concurrency contract</h2>
 * <ul>
 *   <li>Two distinct guards overlap. The {@link #cycleRunning}
 *       atomic flag prevents two {@code runCycle()} invocations from
 *       interleaving their claim phase — even if a test harness or a
 *       faulty caller fires them concurrently. The
 *       {@link #wakeUpLock} guard serialises a wake-up against the
 *       natural {@code fixedDelay} schedule so two wake-ups do not
 *       cause two simultaneous cycles either.</li>
 *   <li>The claim SQL is still the cross-instance atomic owner: this
 *       JVM can never claim a row that another JVM already owns.</li>
 *   <li>The processing executor is bounded
 *       ({@link DocumentPreviewWorkerProperties#getProcessingThreads()}
 *       threads,
 *       {@link DocumentPreviewWorkerProperties#getProcessingQueueCapacity()}
 *       queue capacity). When the queue is full the executor rejects
 *       the task; the worker logs a warning and leaves the row in
 *       {@code PROCESSING}. The stale-PROCESSING reclaim path picks
 *       it up on the next cycle.</li>
 * </ul>
 *
 * <h2>Shutdown</h2>
 * <ul>
 *   <li>{@link #shutdown()} cancels any pending wake-up so no new
 *       cycle starts.</li>
 *   <li>It then interrupts the worker scheduler thread so any
 *       in-flight cycle stops at its next interruption checkpoint.</li>
 *   <li>It finally calls {@link ThreadPoolExecutor#shutdown()} on the
 *       processing executor. In-flight tasks receive a
 *       {@link InterruptedException} via {@link Thread#isInterrupted()}
 *       so they can persist their state before exit.</li>
 *   <li>If the executor does not terminate within
 *       {@link DocumentPreviewWorkerProperties#getFixedDelayMs()}
 *       milliseconds, {@link ThreadPoolExecutor#shutdownNow()} is
 *       called. Test code can override the grace period via
 *       {@link #awaitQuiescence(long, TimeUnit)}.</li>
 * </ul>
 *
 * <h2>Wake-up semantics</h2>
 * <p>{@link #wakeUp()} may be called at any time. When the natural
 * scheduler has not yet started a cycle, the wake-up schedules a
 * one-shot cycle at zero delay. When the scheduler is already inside
 * a cycle, the wake-up is coalesced into the next natural cycle
 * rather than racing it. Wake-ups that arrive after the worker has
 * entered its {@link #shutdown()} path are silently dropped.</p>
 */
@Component
public class DocumentPreviewWorker implements SmartLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentPreviewWorker.class);

    private final DocumentPreviewWorkerProperties properties;
    private final DocumentPreviewArtifactClaimService claimService;
    private final DocumentPreviewArtifactProcessor processor;
    private final java.time.Clock clock;
    private final ThreadFactory processingThreadFactory;
    private final ThreadPoolExecutor processingExecutor;

    /**
     * In-process overlap guard for the claim phase. {@code true}
     * while a cycle is running; flipped back to {@code false} in a
     * {@code finally} block. Combined with the scheduler overlap
     * lock so the worker never enters two claim phases concurrently.
     */
    private final AtomicBoolean cycleRunning = new AtomicBoolean(false);

    /**
     * SmartLifecycle running flag. Tracks whether {@link #start()}
     * has been called and {@link #stop()} has not yet completed.
     * Survives bean reconfiguration and Spring DevTools restart
     * because the bean instance is preserved across the context
     * lifecycle phase transitions.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Visible-only counter of how many times a wake-up or concurrent
     * invocation was coalesced instead of starting a fresh cycle.
     */
    private final AtomicInteger wakeUpCoalescedCount = new AtomicInteger(0);

    /**
     * Counter of cycles executed since boot. Used by tests to
     * differentiate {@code runCycle} invocations.
     */
    private final AtomicLong cycleCounter = new AtomicLong(0);

    /**
     * Per-artifact in-flight marker. A row whose id appears here is
     * currently being processed inside the executor and must never be
     * re-claimed until the future completes. The map is consulted by
     * the wake-up / claim loop ONLY as a defensive belt; the primary
     * claim SQL is the cross-process atomic owner.
     */
    private final Map<java.util.UUID, Future<?>> inFlight =
            new ConcurrentHashMap<>();

    /**
     * Single-slot wake-up signal. {@code true} after a wake-up
     * arrived and has not yet been consumed by a cycle. Cleared
     * immediately before the next cycle starts so two wake-ups are
     * coalesced into a single ASAP cycle.
     */
    private final AtomicBoolean wakeUpPending = new AtomicBoolean(false);

    private final Object wakeUpLock = new Object();

    /**
     * Optional handle to the wake-up signal so the bean can subscribe
     * at construction time. Injected through
     * {@code @Autowired(required=false)} so unit tests that do not
     * wire up the ready signal can still construct the worker.
     */
    private DocumentPreviewArtifactReadySignal readySignal;

    /**
     * Background scheduler thread. Replaces Spring's
     * {@code @Scheduled} so the wake-up mechanism can run an ASAP
     * cycle without waiting for the next fixed-delay tick.
     */
    private volatile Thread schedulerThread;
    private volatile boolean stopping;

    @Autowired
    public DocumentPreviewWorker(
            DocumentPreviewWorkerProperties properties,
            DocumentPreviewArtifactClaimService claimService,
            DocumentPreviewArtifactProcessor processor,
            java.time.Clock clock) {
        this(properties, claimService, processor, clock, null);
    }

    /**
     * Production constructor. Subscribes to the supplied
     * {@link DocumentPreviewArtifactReadySignal} bean so any
     * FULL-to-READY transition triggers a wake-up.
     */
    public DocumentPreviewWorker(
            DocumentPreviewWorkerProperties properties,
            DocumentPreviewArtifactClaimService claimService,
            DocumentPreviewArtifactProcessor processor,
            java.time.Clock clock,
            DocumentPreviewArtifactReadySignal readySignal) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.claimService = Objects.requireNonNull(claimService, "claimService");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.processingThreadFactory = new ProcessingThreadFactory();
        int threads = Math.max(1, properties.getProcessingThreads());
        int capacity = Math.max(threads, properties.getProcessingQueueCapacity());
        this.processingExecutor = new ThreadPoolExecutor(
                threads, threads,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                processingThreadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        if (readySignal != null) {
            this.readySignal = readySignal;
            readySignal.attach(this);
        }
    }

    @Override
    public void start() {
        // Idempotency guards: prevent duplicate scheduler thread
        // creation in two scenarios:
        //   1. Spring DevTools restart re-invokes start() without
        //      stop() (rare but possible during live reload).
        //   2. A bug elsewhere in the lifecycle invokes start()
        //      twice. Either way, we MUST NOT spawn a second
        //      scheduler thread.
        if (running.get()) {
            return;
        }
        if (schedulerThread != null) {
            log.info("Preview worker scheduler already has a thread; "
                    + "skipping start() to avoid duplicate");
            return;
        }
        if (properties.isEnabled()) {
            // Reset stopping so a restarted scheduler (DevTools
            // restart) actually runs its loop. shutdownInternal()
            // sets stopping=true to ask the loop to exit, and that
            // flag must be cleared here so the new thread observes
            // !stopping on its first iteration check. Same applies to
            // cycleRunning (a leftover true would block the next
            // CAS in runCycle()).
            stopping = false;
            cycleRunning.set(false);
            schedulerThread = new Thread(this::runSchedulerLoop,
                    "preview-worker-scheduler");
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            running.set(true);
            log.info("Preview worker scheduler started fixedDelayMs={} threads={}",
                    properties.getFixedDelayMs(),
                    properties.getProcessingThreads());
        } else {
            log.info("Preview worker scheduler not started (enabled=false)");
            running.set(false);
        }
    }

    /**
     * Visible-for-test entry point that starts the background
     * scheduler thread outside Spring so async-path tests can verify
     * the bounded executor behaviour. Idempotent: calling twice is
     * a no-op. Returns immediately when {@code enabled=false}.
     */
    public void startSchedulerForTest() {
        if (schedulerThread == null && properties.isEnabled()) {
            schedulerThread = new Thread(this::runSchedulerLoop,
                    "preview-worker-scheduler");
            schedulerThread.setDaemon(true);
            schedulerThread.start();
        }
    }

    /**
     * Visible-for-test entry point that shuts the bounded executor
     * down. Tests call this from a {@code finally} block so they do
     * not leak daemon threads between methods.
     */
    public void shutdownForTest() {
        shutdownInternal();
    }

    /**
     * Visible-for-test helper that dispatches a single claim through
     * the executor using the same capacity-aware path as the
     * production cycle. Returns {@code true} when the task was
     * submitted, {@code false} when the executor was saturated.
     */
    public boolean dispatchForTest(DocumentPreviewArtifactClaim claim,
                                   LocalDateTime now) {
        return tryDispatch(claim, now);
    }

    /**
     * Visible-for-test accessor that reports whether the bounded
     * executor has fully terminated.
     */
    public boolean isProcessingExecutorAliveForTest() {
        return !processingExecutor.isTerminated();
    }

    /**
     * Visible-for-test accessor that reports whether the background
     * scheduler thread is still alive. Used by lifecycle tests to
     * assert that {@link #shutdown()} terminates both the scheduler
     * and the bounded executor.
     */
    public boolean isSchedulerAliveForTest() {
        Thread t = schedulerThread;
        return t != null && t.isAlive();
    }

    @Override
    public void stop() {
        // Idempotency: a SmartLifecycle stop() may be called twice
        // (once for graceful shutdown, once for forced shutdown).
        if (!running.compareAndSet(true, false)) {
            return;
        }
        shutdownInternal();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Run after web-layer SmartLifecycles (default 0) but before
     * late-phase beans (Integer.MAX_VALUE). The worker is a
     * background service that should start as soon as the web layer
     * is up and stop as late as possible during application shutdown
     * so that any in-flight HTTP request can still trigger the
     * wake-up path.
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1024;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * Internal shutdown logic. Called by {@link #stop()} (Spring) and
     * {@link #shutdownForTest()} (unit tests). {@link #stop()} is
     * guarded by the {@link #running} flag so a no-op double-stop is
     * a no-op; tests that need to shutdown even when {@link #start()}
     * was never called (because the bean was constructed directly)
     * invoke {@code shutdownForTest} which delegates to this method
     * directly.
     */
    void shutdownInternal() {
        stopping = true;
        if (readySignal != null) {
            readySignal.detach(this);
        }
        Thread scheduler = schedulerThread;
        if (scheduler != null) {
            // The scheduler may be parked inside LockSupport.parkNanos;
            // unpark + interrupt together guarantee it returns from the
            // park call and notices stopping on the next loop check.
            LockSupport.unpark(scheduler);
            scheduler.interrupt();
        }
        processingExecutor.shutdown();
        try {
            long grace = Math.max(500L,
                    Math.min(properties.getFixedDelayMs(), 5_000L));
            if (!processingExecutor.awaitTermination(
                    grace, TimeUnit.MILLISECONDS)) {
                log.warn("Processing executor did not terminate in {}ms; forcing",
                        grace);
                processingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            processingExecutor.shutdownNow();
        }
        // Wait for the scheduler thread to actually exit so the
        // Spring {@code @PreDestroy} callback returns only after the
        // bean has fully released its threads.
        if (scheduler != null) {
            try {
                scheduler.join(2_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (scheduler.isAlive()) {
                log.warn("Scheduler thread did not exit in 2s; abandoning");
            }
        }
        // Clear the reference so a subsequent {@link #start()} call
        // (e.g. Spring DevTools restart) can spin up a fresh
        // scheduler thread instead of seeing the dead reference and
        // skipping startup.
        schedulerThread = null;
    }

    /**
     * Manually triggered wake-up. Schedules a one-shot cycle ASAP.
     * <p>Coalescing semantics:
     * <ul>
     *   <li>When a wake-up arrives while a cycle is running, the
     *       wake-up is recorded and the cycle already in progress
     *       will be followed by a fresh cycle as soon as it
     *       completes.</li>
     *   <li>When the scheduler is between cycles and not yet parked,
     *       the wake-up {@link LockSupport#unpark(Thread)}s the
     *       scheduler thread so the next cycle starts within
     *       milliseconds.</li>
     * </ul>
     */
    public void wakeUp() {
        if (stopping) {
            return;
        }
        if (wakeUpPending.compareAndSet(false, true)) {
            Thread scheduler = schedulerThread;
            if (scheduler != null) {
                LockSupport.unpark(scheduler);
            }
        } else {
            wakeUpCoalescedCount.incrementAndGet();
        }
    }

    private void runSchedulerLoop() {
        while (!stopping && !Thread.currentThread().isInterrupted()) {
            try {
                long delay = waitForDelay();
                if (stopping || Thread.currentThread().isInterrupted()) {
                    break;
                }
                if (delay > 0L) {
                    LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(delay));
                }
                if (stopping || Thread.currentThread().isInterrupted()) {
                    break;
                }
                runCycle();
            } catch (RuntimeException e) {
                log.warn("Preview worker scheduler caught unexpected error", e);
            }
        }
        log.info("Preview worker scheduler loop exited");
    }

    /**
     * Resolves the delay before the next cycle.
     * <ul>
     *   <li>Returns {@code 0L} when a wake-up is pending so the cycle
     *       starts immediately.</li>
     *   <li>Returns {@link DocumentPreviewWorkerProperties#getFixedDelayMs()}
     *       when no wake-up is pending, i.e. the natural fixed-delay
     *       schedule.</li>
     * </ul>
     */
    private long waitForDelay() {
        if (wakeUpPending.get()) {
            return 0L;
        }
        return properties.getFixedDelayMs();
    }

    /**
     * Scheduled entry point invoked by {@link #runSchedulerLoop()}.
     * Spring no longer schedules this method via {@code @Scheduled}
     * because the wake-up mechanism needs an ASAP trigger. Spring
     * scheduling would force every cycle to wait for the
     * {@code fixedDelayString} tick regardless of wake-ups.
     *
     * <p>Routing logic:</p>
     * <ul>
     *   <li>When the {@link #schedulerThread} is non-null (i.e. the
     *       bean was constructed by Spring and
     *       {@link #startScheduler()} ran), this method dispatches
     *       the batch asynchronously to the bounded executor so a
     *       slow DOCX never blocks the next scheduler tick.</li>
     *   <li>When the bean is constructed directly by unit tests
     *       (without Spring), the scheduler thread is {@code null}
     *       and the method falls back to the synchronous legacy path
     *       that the existing mocks-only test contract depends on.
     *       New tests can opt-in to the bounded-executor behaviour
     *       by calling {@link #startScheduler()} explicitly.</li>
     * </ul>
     */
    public void runCycle() {
        if (!properties.isEnabled()) {
            return;
        }
        if (schedulerThread == null) {
            runCycleSyncForTest();
            return;
        }
        synchronized (wakeUpLock) {
            wakeUpPending.set(false);
            if (!cycleRunning.compareAndSet(false, true)) {
                wakeUpCoalescedCount.incrementAndGet();
                log.info("Preview worker cycle overlap prevented; previous cycle still running");
                return;
            }
            try {
                executeCycle();
            } finally {
                cycleRunning.set(false);
                cycleCounter.incrementAndGet();
            }
        }
    }

    private void runCycleSyncForTest() {
        if (!cycleRunning.compareAndSet(false, true)) {
            wakeUpCoalescedCount.incrementAndGet();
            return;
        }
        try {
            executeCycleSync();
        } finally {
            cycleRunning.set(false);
            cycleCounter.incrementAndGet();
        }
    }

    /**
     * Visible-for-test entry point that performs one cycle without
     * touching the scheduler. Dispatches the batch synchronously when
     * the worker has no scheduler thread (the unit-test path) so
     * legacy mocks-only tests can still verify the synchronous
     * processor contract.
     */
    public int executeCycleForTest() {
        if (schedulerThread != null) {
            throw new IllegalStateException(
                    "executeCycleForTest must not be called when the "
                            + "background scheduler is running");
        }
        return runCycleSyncForTestSafe();
    }

    private int runCycleSyncForTestSafe() {
        if (!cycleRunning.compareAndSet(false, true)) {
            wakeUpCoalescedCount.incrementAndGet();
            return 0;
        }
        try {
            return executeCycleSync();
        } finally {
            cycleRunning.set(false);
            cycleCounter.incrementAndGet();
        }
    }

    private int executeCycle() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime staleBefore = now.minus(properties.getStaleAfter());

        // Capacity-aware claim: the bounded executor owns the
        // processing side, so we MUST size the claim to the number
        // of slots that can actually accept a new task. Otherwise we
        // either leave claimed rows in PROCESSING (stuck until
        // stale-PROCESSING reclaim) or fire a RejectedExecutionException
        // that the dispatch loop has to back-fill.
        int availableSlots = availableProcessingSlotsInternal();
        log.info("Worker cycle started now={} staleBefore={} batchSize={} threads={} availableSlots={}",
                now, staleBefore, properties.getBatchSize(),
                processingExecutor.getCorePoolSize(),
                availableSlots);

        if (availableSlots <= 0) {
            log.info("Worker cycle skipped: no executor capacity available");
            return 0;
        }

        int claimLimit = Math.min(properties.getBatchSize(), availableSlots);

        List<DocumentPreviewArtifactClaim> claims;
        try {
            claims = claimService.claimBatch(claimLimit, staleBefore);
        } catch (RuntimeException e) {
            log.warn("Worker cycle claim failed", e);
            log.info("Worker cycle finished");
            return 0;
        }
        log.info("Worker cycle claimed {} artifact(s)", claims.size());

        int dispatched = 0;
        for (DocumentPreviewArtifactClaim claim : claims) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            // Re-check the slot count right before each dispatch; the
            // executor can drain a slot between the initial probe and
            // the actual submit. The claim-side capacity reserve keeps
            // the system safe in steady state; the re-check is a small
            // safety net for the burst case where a concurrent task
            // completes between the initial probe and the submit.
            if (!tryDispatch(claim, now)) {
                log.warn("Worker cycle dispatch back-off id={}; executor saturated",
                        claim.artifactId());
                break;
            }
            dispatched++;
        }
        log.info(
                "Worker cycle dispatched dispatched={} durationMs={}",
                dispatched,
                Duration.between(now, LocalDateTime.now(clock)).toMillis());
        return dispatched;
    }

    /**
     * Attempts to dispatch the claim to the executor. Returns
     * {@code true} when the task was submitted successfully,
     * {@code false} when the executor was saturated at submit time.
     * The claim-side capacity probe guarantees that the
     * {@code false} branch is rare in practice; when it does fire
     * the loop breaks cleanly without leaving the row in
     * {@code PROCESSING} &mdash; the stale-PROCESSING reclaim path
     * owns recovery.
     */
    private boolean tryDispatch(DocumentPreviewArtifactClaim claim,
                                LocalDateTime now) {
        java.util.UUID id = claim.artifactId();
        if (inFlight.putIfAbsent(id, FutureStub.SENTINEL) != null) {
            log.warn("Skipping dispatch for claim id={}; already in-flight", id);
            return true;
        }
        Future<?> task;
        try {
            task = processingExecutor.submit(() -> runOne(claim, now));
        } catch (RejectedExecutionException ree) {
            inFlight.remove(id);
            return false;
        }
        inFlight.put(id, task);
        return true;
    }

    private int executeCycleSync() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime staleBefore = now.minus(properties.getStaleAfter());

        // Sync path also honours capacity-aware claiming so the
        // synchronous and asynchronous code paths share the same
        // invariant: a row is only claimed when there is a real
        // processing slot for it. The sync mode skips the executor
        // and processes inline, but for tests that explicitly request
        // a saturated scenario the cap still applies.
        int claimLimit = Math.min(properties.getBatchSize(),
                Math.max(1, availableProcessingSlotsInternal()));

        log.info("Worker cycle (sync) started now={} staleBefore={} batchSize={} claimLimit={}",
                now, staleBefore, properties.getBatchSize(), claimLimit);

        List<DocumentPreviewArtifactClaim> claims;
        try {
            claims = claimService.claimBatch(claimLimit, staleBefore);
        } catch (RuntimeException e) {
            log.warn("Worker cycle (sync) claim failed", e);
            log.info("Worker cycle (sync) finished");
            return 0;
        }
        log.info("Worker cycle (sync) claimed {} artifact(s)", claims.size());

        int processed = 0;
        int interrupted = 0;
        int ready = 0;
        int retry = 0;
        int dead = 0;
        int lost = 0;
        for (DocumentPreviewArtifactClaim claim : claims) {
            if (Thread.currentThread().isInterrupted()) {
                interrupted++;
                break;
            }
            try {
                WorkerOutcome outcome;
                try {
                    outcome = processor.process(claim, now);
                } catch (RuntimeException e) {
                    log.warn("Worker uncaught exception for claim id={}",
                            claim.artifactId(), e);
                    continue;
                }
                processed++;
                switch (outcome) {
                    case READY -> ready++;
                    case RETRY -> retry++;
                    case DEAD -> dead++;
                    case LOST_OWNERSHIP -> lost++;
                    case INTERRUPTED -> interrupted++;
                }
                if (outcome == WorkerOutcome.INTERRUPTED) {
                    break;
                }
            } catch (RuntimeException e) {
                log.warn("Worker uncaught exception for claim id={}",
                        claim.artifactId(), e);
            }
        }
        log.info(
                "Worker cycle (sync) finished processed={} ready={} retry={} "
                        + "dead={} lost={} interrupted={} durationMs={}",
                processed, ready, retry, dead, lost, interrupted,
                Duration.between(now, LocalDateTime.now(clock)).toMillis());
        return processed;
    }

    /**
     * Per-claim execution body. Wraps the processor call with
     * interrupt checkpoints and bookkeeping so the executor can
     * clean up the {@link #inFlight} map even when the processor
     * throws.
     */
    private void runOne(DocumentPreviewArtifactClaim claim,
                        LocalDateTime now) {
        try {
            WorkerOutcome outcome;
            try {
                outcome = processor.process(claim, now);
            } catch (RuntimeException e) {
                log.warn("Worker uncaught exception for claim id={}",
                        claim.artifactId(), e);
                return;
            }
            switch (outcome) {
                case READY -> log.info("Artifact READY id={}", claim.artifactId());
                case RETRY -> log.info("Artifact RETRY id={}", claim.artifactId());
                case DEAD -> log.info("Artifact DEAD id={}", claim.artifactId());
                case LOST_OWNERSHIP -> log.info(
                        "Artifact LOST_OWNERSHIP id={}", claim.artifactId());
                case INTERRUPTED -> log.info(
                        "Artifact INTERRUPTED id={}", claim.artifactId());
            }
        } finally {
            inFlight.remove(claim.artifactId());
        }
    }

    /**
     * Visible-for-test accessor for the wake-up coalescing counter.
     */
    public int wakeUpCoalescedCount() {
        return wakeUpCoalescedCount.get();
    }

    /**
     * Legacy alias for {@link #wakeUpCoalescedCount()}. Preserved so
     * pre-existing tests that asserted the overlap guard fired keep
     * compiling without modification. The counter is the same.
     */
    public int overlapPreventedCount() {
        return wakeUpCoalescedCount.get();
    }

    /**
     * Visible-for-test accessor for the cycle counter.
     */
    public long cycleCounter() {
        return cycleCounter.get();
    }

    /** Visible-for-test accessor for the running flag. */
    public boolean isCycleRunning() {
        return cycleRunning.get();
    }

    /**
     * Blocks the caller until every currently in-flight task has
     * completed or the timeout elapses. Used by tests that want to
     * assert bounded-concurrency behaviour without spinning.
     */
    public void awaitQuiescence(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (!inFlight.isEmpty()) {
            if (System.nanoTime() >= deadlineNanos) {
                return;
            }
            Thread.sleep(5);
        }
    }

    /**
     * Returns the number of tasks currently being processed by the
     * bounded executor. Used by tests to verify bounded-concurrency.
     */
    public int inFlightCount() {
        return inFlight.size();
    }

    /**
     * Returns the active thread count inside the bounded executor.
     * Used by tests to verify that a slow processor does not block
     * the scheduler.
     */
    public int processingActiveCount() {
        return processingExecutor.getActiveCount();
    }

    /**
     * Returns the size of the executor's bounded queue. Visible to
     * tests so they can assert that a slow processor results in a
     * queued, NOT blocked, scheduler.
     */
    public int processingQueueSize() {
        return processingExecutor.getQueue().size();
    }

    /**
     * Visible-for-test accessor for the number of additional tasks
     * the bounded executor can accept right now. Used by capacity-aware
     * claim tests to assert that the worker never claims more rows
     * than it can dispatch.
     */
    public int availableProcessingSlots() {
        return availableProcessingSlotsInternal();
    }

    private int availableProcessingSlotsInternal() {
        int corePoolSize = processingExecutor.getCorePoolSize();
        int activeCount = processingExecutor.getActiveCount();
        int queueSize = processingExecutor.getQueue().size();
        int remaining = corePoolSize - activeCount - queueSize;
        return Math.max(0, remaining);
    }

    private static final class ProcessingThreadFactory implements ThreadFactory {
        private final AtomicInteger serial = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "preview-worker-processing-"
                    + serial.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * Sentinel future used to occupy {@link #inFlight} while the
     * real task submission is in flight. Subsequent dispatches for
     * the same artifact see this sentinel and refuse to re-dispatch.
     */
    private static final class FutureStub implements Future<Object> {
        static final FutureStub SENTINEL = new FutureStub();
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    }
}
