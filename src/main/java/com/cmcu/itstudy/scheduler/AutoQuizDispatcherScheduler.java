package com.cmcu.itstudy.scheduler;

import com.cmcu.itstudy.config.AutoQuizDispatchProperties;
import com.cmcu.itstudy.service.contract.AutoQuizDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * Phase&nbsp;2D Auto Quiz dispatcher scheduler.
 *
 * <p>A dedicated fixed-delay loop that calls
 * {@link AutoQuizDispatcherService#runCycle()} on every tick. The
 * scheduler is wired ONLY when
 * {@code app.auto-quiz.dispatch.enabled=true}; when the property
 * is {@code false} the bean is absent from the context and Spring
 * never schedules a cycle.</p>
 *
 * <h2>Why a dedicated scheduler (and not {@code @Scheduled})?</h2>
 * <p>The dispatcher uses a manual {@link Thread} (mirroring
 * {@link DocumentPreviewWorker}) so future wake-up signals can
 * trigger an ASAP cycle without modifying the public scheduling
 * loop. For Phase&nbsp;2D the wake-up mechanism is not in scope
 * (no FULL preview READY needs to nudge the dispatcher), so the
 * current implementation runs strictly on
 * {@link AutoQuizDispatchProperties#getFixedDelayMs()}.</p>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@link #start()} starts a daemon scheduler thread when
 *       the bean is constructed (i.e. when the property is
 *       {@code true}).</li>
 *   <li>{@link #stop()} stops the scheduler and waits for the
 *       current cycle to drain, bounded by
 *       {@link AutoQuizDispatchProperties#getFixedDelayMs()}.</li>
 *   <li>{@link SmartLifecycle#getPhase()} returns
 *       {@link Integer#MAX_VALUE} so the scheduler starts AFTER
 *       web-layer SmartLifecycles and stops BEFORE early-phase
 *       beans during application shutdown.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(
        prefix = "app.auto-quiz.dispatch",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class AutoQuizDispatcherScheduler implements SmartLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(AutoQuizDispatcherScheduler.class);

    private final AutoQuizDispatchProperties properties;
    private final AutoQuizDispatcherService dispatcherService;

    private volatile Thread schedulerThread;
    private volatile boolean stopping;

    /**
     * Visible-for-test flag set to {@code true} between
     * {@link #start()} and {@link #stop()}. Tests use this to
     * confirm lifecycle wiring.
     */
    private volatile boolean running;

    @Autowired
    public AutoQuizDispatcherScheduler(
            AutoQuizDispatchProperties properties,
            AutoQuizDispatcherService dispatcherService) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.dispatcherService = Objects.requireNonNull(
                dispatcherService, "dispatcherService");
        properties.validate();
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (schedulerThread != null) {
            return;
        }
        stopping = false;
        schedulerThread = new Thread(this::runSchedulerLoop,
                "auto-quiz-dispatcher-scheduler");
        schedulerThread.setDaemon(true);
        schedulerThread.start();
        running = true;
        log.info(
                "Auto Quiz dispatcher scheduler started fixedDelayMs={} "
                        + "batchSize={} webhook={}",
                properties.getFixedDelayMs(),
                properties.getBatchSize(),
                properties.safeWebhookSummary());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        stopping = true;
        Thread t = schedulerThread;
        if (t != null) {
            LockSupport.unpark(t);
            t.interrupt();
        }
        long grace = Math.max(500L,
                Math.min(properties.getFixedDelayMs(), 5_000L));
        if (t != null) {
            try {
                t.join(grace);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (t.isAlive()) {
                log.warn(
                        "Auto Quiz dispatcher scheduler thread did not "
                                + "exit in {}ms; abandoning",
                        grace);
            }
        }
        schedulerThread = null;
        running = false;
        log.info("Auto Quiz dispatcher scheduler stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Phase {@code Integer.MAX_VALUE}: start AFTER web-layer
     * SmartLifecycles, stop BEFORE early-phase beans during
     * shutdown.
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    private void runSchedulerLoop() {
        log.info(
                "Auto Quiz dispatcher scheduler loop entered "
                        + "fixedDelayMs={}",
                properties.getFixedDelayMs());
        while (!stopping
                && !Thread.currentThread().isInterrupted()) {
            try {
                runOneCycle();
                LockSupport.parkNanos(this,
                        Duration.ofMillis(properties.getFixedDelayMs())
                                .toNanos());
            } catch (RuntimeException e) {
                log.warn(
                        "Auto Quiz dispatcher scheduler caught unexpected "
                                + "error; continuing",
                        e);
            }
        }
        log.info("Auto Quiz dispatcher scheduler loop exited");
    }

    /**
     * Visible-for-test entry point that runs a single cycle
     * synchronously. The scheduler thread also calls this method
     * inside its loop.
     */
    public AutoQuizDispatcherService.CycleOutcome runOneCycle() {
        try {
            return dispatcherService.runCycle();
        } catch (RuntimeException e) {
            log.warn("Auto Quiz dispatcher cycle threw", e);
            return AutoQuizDispatcherService.CycleOutcome.noop();
        }
    }

    /**
     * Visible-for-test accessor for the running flag.
     */
    public boolean isRunningForTest() {
        return running;
    }
}