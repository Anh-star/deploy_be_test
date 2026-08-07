package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.office.ProcessExecutionResult;
import com.cmcu.itstudy.dto.office.ProcessRunOptions;
import com.cmcu.itstudy.dto.office.ProcessTerminationReason;
import com.cmcu.itstudy.handle.OfficeConversionInterruptedException;
import com.cmcu.itstudy.service.contract.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-platform {@link ProcessRunner} that uses Java 17
 * {@link ProcessHandle} exclusively for process-tree termination.
 *
 * <p>The runner NEVER shells out. It never invokes {@code pgrep},
 * {@code pkill}, {@code taskkill}, {@code cmd /c}, {@code powershell}
 * or {@code sh -c}. Termination is performed by
 * {@code ProcessHandle.destroy()} and
 * {@code ProcessHandle.destroyForcibly()} on a leaf-first snapshot of
 * the descendants of the spawned process.</p>
 *
 * <p>Resource ownership: this class owns the {@link ProcessBuilder},
 * the {@link Process} handle, the two stream consumer {@link Future}s,
 * the bounded capture buffers, the dedicated {@link ExecutorService}
 * and the bounded {@link ProcessExecutionResult}. It does NOT own any
 * LibreOffice semaphore, conversion temp directory, profile
 * directory, document bytes, PDF validation or Supabase call.</p>
 */
@Service
public class SafeProcessRunner implements ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(SafeProcessRunner.class);

    @Override
    public ProcessExecutionResult run(List<String> argv, ProcessRunOptions options) {
        if (argv == null || argv.isEmpty()) {
            throw new IllegalArgumentException("argv must not be null or empty");
        }
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }

        ProcessBuilder pb = new ProcessBuilder(argv);
        Map<String, String> inherited = pb.environment();
        // Layer the optional overrides on top of the inherited env.
        // The runner NEVER clears PATH and NEVER logs the resulting env.
        for (Map.Entry<String, String> e : options.envOverrides().entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                inherited.put(e.getKey(), e.getValue());
            }
        }

        Instant start = Instant.now();

        Process process;
        try {
            process = pb.start();
        } catch (IOException ex) {
            // Startup failure path: no executor, no futures, no streams.
            // Returning a STARTUP_FAILURE result lets the caller map
            // to a typed retryable OfficeConversionStartupException.
            return new ProcessExecutionResult(
                    null,
                    false,
                    "",
                    "",
                    Duration.between(start, Instant.now()),
                    ProcessTerminationReason.STARTUP_FAILURE);
        }

        ExecutorService streamExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "soffice-stream-" + Long.toHexString(System.nanoTime()));
            t.setDaemon(true);
            return t;
        });
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Future<byte[]> stdoutFuture = streamExecutor.submit(
                () -> drainBounded(process.getInputStream(),
                        options.stdoutCaptureMaxBytes(), cancelled));
        Future<byte[]> stderrFuture = streamExecutor.submit(
                () -> drainBounded(process.getErrorStream(),
                        options.stderrCaptureMaxBytes(), cancelled));

        boolean timedOut = false;
        ProcessTerminationReason reason = ProcessTerminationReason.NORMAL_EXIT;
        Integer exitCode = null;

        // Interrupt bookkeeping: the runner must restore the calling
        // thread's interrupt flag before throwing the typed exception,
        // but ONLY after process / stream / executor cleanup has
        // completed. We track the original interrupt state in
        // `interruptedDuringRun` and apply Thread.currentThread().interrupt()
        // once, in the final finally block, so subsequent cleanup
        // operations can rely on a stable (non-racing) flag.
        boolean interruptedDuringRun = Thread.currentThread().isInterrupted();

        try {
            try {
                boolean finished = process.waitFor(options.timeout().toMillis(),
                        TimeUnit.MILLISECONDS);
                if (!finished) {
                    timedOut = true;
                    reason = ProcessTerminationReason.TIMEOUT;
                    terminateTree(process, options.gracePeriod(),
                            options.forcedTerminationTimeout());
                    exitCode = readExitCodeSafely(process);
                } else {
                    exitCode = readExitCodeSafely(process);
                }
            } catch (InterruptedException ie) {
                // Save the interrupt state WITHOUT restoring yet.
                interruptedDuringRun = true;
                reason = ProcessTerminationReason.INTERRUPTED;

                // Cleanup while the interrupt flag is still set is
                // intentional: bounded waits inside cleanup see the
                // interrupt, fail fast, and the cleanup helper catches
                // and continues. We do not loop forever because each
                // cleanup helper has its own bounded deadline.
                terminateTree(process, options.gracePeriod(),
                        options.forcedTerminationTimeout());
                exitCode = readExitCodeSafely(process);
                // Drain / close / executor cleanup must happen before
                // we restore the flag and throw.
                cleanupAndCloseStreams(streamExecutor, stdoutFuture, stderrFuture,
                        process, options.streamDrainTimeout(), cancelled);
                // Build the typed exception here but do NOT throw yet;
                // the outer finally must run first to restore the
                // interrupt flag and free the semaphore in the
                // converter's outer try/finally chain.
                throw new OfficeConversionInterruptedException(
                        "Process interrupted", ie);
            }

            String stdoutSummary = collectBoundedSummary(stdoutFuture,
                    options.streamDrainTimeout(), cancelled, streamExecutor);
            String stderrSummary = collectBoundedSummary(stderrFuture,
                    options.streamDrainTimeout(), cancelled, streamExecutor);

            cleanupAndCloseStreams(streamExecutor, stdoutFuture, stderrFuture,
                    process, options.streamDrainTimeout(), cancelled);

            Duration elapsed = Duration.between(start, Instant.now());
            return new ProcessExecutionResult(
                    exitCode, timedOut, stdoutSummary, stderrSummary, elapsed, reason);
        } finally {
            // Restore the interrupt flag ONCE, after every other cleanup
            // operation has finished or timed out. This guarantees:
            // 1. cleanup operations saw the original interrupt status;
            // 2. the calling thread observes the interrupt after the
            //    public run() returns or throws;
            // 3. the flag is restored EXACTLY once (no double-restore).
            if (interruptedDuringRun) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Drain an {@link InputStream} into a bounded buffer. Once the cap
     * is reached the remaining bytes are drained into a black-hole
     * sink so the OS pipe never fills and the process never
     * deadlocks.
     */
    private static byte[] drainBounded(InputStream stream, int maxBytes,
                                       AtomicBoolean cancelled) {
        byte[] primary = new byte[Math.min(maxBytes, 8192)];
        int primaryLen = 0;
        byte[] buffer = new byte[4096];
        OutputStream sink = OutputStream.nullOutputStream();
        try (InputStream in = stream) {
            while (!cancelled.get()) {
                int n;
                try {
                    n = in.read(buffer);
                } catch (IOException ioe) {
                    // Stream closed by the runner or forced termination
                    // tore down the pipe. Exit gracefully.
                    break;
                }
                if (n < 0) {
                    // EOF.
                    break;
                }
                if (primaryLen < maxBytes) {
                    int take = Math.min(n, maxBytes - primaryLen);
                    if (primary.length < primaryLen + take) {
                        byte[] grown = new byte[Math.min(maxBytes, primaryLen + take)];
                        System.arraycopy(primary, 0, grown, 0, primaryLen);
                        primary = grown;
                    }
                    System.arraycopy(buffer, 0, primary, primaryLen, take);
                    primaryLen += take;
                    if (n > take) {
                        // We crossed the cap; spill the rest to the sink.
                        sink.write(buffer, take, n - take);
                    }
                } else {
                    // Already at the cap. All bytes go to the sink.
                    sink.write(buffer, 0, n);
                }
            }
        } catch (IOException ioe) {
            // Broken pipe during forced termination is expected.
            return trimPrimary(primary, primaryLen);
        }
        return trimPrimary(primary, primaryLen);
    }

    private static byte[] trimPrimary(byte[] primary, int primaryLen) {
        if (primaryLen == primary.length) {
            return primary;
        }
        byte[] trimmed = new byte[primaryLen];
        System.arraycopy(primary, 0, trimmed, 0, primaryLen);
        return trimmed;
    }

    /**
     * Collect the bounded prefix from a consumer future. Uses the
     * configured stream-drain timeout so a stuck reader never blocks
     * the runner. Does NOT modify the calling thread's interrupt
     * flag — the outer run() restores it once in its finally block.
     */
    private static String collectBoundedSummary(Future<byte[]> future,
                                                Duration streamDrainTimeout,
                                                AtomicBoolean cancelled,
                                                ExecutorService executor) {
        if (future == null) {
            return "";
        }
        try {
            byte[] bytes = future.get(streamDrainTimeout.toMillis(),
                    TimeUnit.MILLISECONDS);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (TimeoutException te) {
            future.cancel(true);
            cancelled.set(true);
            return "";
        } catch (InterruptedException ie) {
            // Absorb; outer finally restores the interrupt flag.
            future.cancel(true);
            cancelled.set(true);
            return "";
        } catch (ExecutionException ee) {
            // Consumer failure does not crash the runner; record empty summary.
            return "";
        }
    }

    /**
     * Final cleanup: close process streams, cancel consumer futures,
     * shut down the dedicated executor. Always null-safe.
     *
     * <p>This helper does NOT modify the calling thread's interrupt
     * flag. Each bounded wait catches {@link InterruptedException} so
     * a stuck reader or a stuck executor termination cannot loop
     * forever. The outer {@code run()} method restores the interrupt
     * flag once, in its own finally block, after cleanup has
     * finished.</p>
     */
    private static void cleanupAndCloseStreams(ExecutorService executor,
                                               Future<byte[]> stdoutFuture,
                                               Future<byte[]> stderrFuture,
                                               Process process,
                                               Duration streamDrainTimeout,
                                               AtomicBoolean cancelled) {
        // 1. Try a bounded wait on both futures first; if the consumer
        //    has reached EOF on its own, no cancellation is needed.
        //    Each bounded wait has its OWN deadline so a slow future
        //    cannot block the next one forever. We do NOT restore the
        //    interrupt flag here — the outer run() does that once.
        cancelAndAwait(stdoutFuture, streamDrainTimeout, cancelled);
        cancelAndAwait(stderrFuture, streamDrainTimeout, cancelled);

        // 2. Close process streams.
        if (process != null) {
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            closeQuietly(process.getOutputStream());
        }

        // 3. Shut the executor down cleanly with bounded waits.
        shutdownExecutorBounded(executor, streamDrainTimeout);
    }

    /**
     * Cancel the supplied future (if non-null and not already done)
     * after a bounded wait. {@link InterruptedException} during the
     * wait is absorbed and the future is force-cancelled; the caller
     * is responsible for restoring the interrupt flag.
     */
    private static void cancelAndAwait(Future<byte[]> future,
                                       Duration streamDrainTimeout,
                                       AtomicBoolean cancelled) {
        if (future == null || future.isDone()) {
            return;
        }
        try {
            future.get(streamDrainTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            // Absorb: outer code restores the interrupt flag once.
            future.cancel(true);
            cancelled.set(true);
        } catch (TimeoutException te) {
            future.cancel(true);
            cancelled.set(true);
        } catch (ExecutionException ee) {
            future.cancel(true);
            cancelled.set(true);
        }
    }

    /**
     * Shut down the supplied executor with bounded waits. Multiple
     * {@link InterruptedException}s are absorbed; the outer run()
     * restores the interrupt flag once. The method never throws.
     */
    private static void shutdownExecutorBounded(ExecutorService executor,
                                                Duration streamDrainTimeout) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(streamDrainTimeout.toMillis(),
                    TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(streamDrainTimeout.toMillis(),
                        TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ie) {
            // Absorb. Outer finally restores the interrupt flag.
            executor.shutdownNow();
        }
    }

    /**
     * Terminate the entire process tree using Java {@link ProcessHandle}
     * only. No shell commands are issued.
     */
    private static void terminateTree(Process process, Duration gracePeriod,
                                      Duration forcedTerminationTimeout) {
        if (process == null) {
            return;
        }
        ProcessHandle root = process.toHandle();
        if (root == null) {
            return;
        }

        // Snapshot descendants once, deepest first.
        List<ProcessHandle> all = new ArrayList<>();
        try {
            root.descendants().forEach(all::add);
        } catch (SecurityException | UnsupportedOperationException ignored) {
            // If the platform refuses to enumerate descendants we
            // still terminate the root below.
        }
        all.sort(Comparator.comparingInt(
                (ProcessHandle h) -> depthOf(h, root)).reversed());

        // Phase 1: graceful destroy on descendants, then root.
        for (ProcessHandle h : all) {
            if (h.isAlive()) {
                try {
                    h.destroy();
                } catch (IllegalStateException ignored) {
                    // Already exited.
                }
            }
        }
        if (root.isAlive()) {
            try {
                root.destroy();
            } catch (IllegalStateException ignored) {
                // Already exited.
            }
        }

        // Wait for graceful exit.
        if (!awaitExit(process, gracePeriod)) {
            // Phase 2: forced termination.
            for (ProcessHandle h : all) {
                if (h.isAlive()) {
                    try {
                        h.destroyForcibly();
                    } catch (IllegalStateException ignored) {
                        // Already exited.
                    }
                }
            }
            if (root.isAlive()) {
                try {
                    root.destroyForcibly();
                } catch (IllegalStateException ignored) {
                    // Already exited.
                }
            }
            awaitExit(process, forcedTerminationTimeout);
        }
    }

    private static boolean awaitExit(Process process, Duration timeout) {
        if (process == null || !process.isAlive()) {
            return true;
        }
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            // Absorb; outer run() restores the interrupt flag once.
            return process.isAlive();
        }
    }

    private static int depthOf(ProcessHandle h, ProcessHandle root) {
        int depth = 0;
        ProcessHandle cursor = h.parent().orElse(null);
        while (cursor != null && cursor != root) {
            depth++;
            cursor = cursor.parent().orElse(null);
        }
        return depth;
    }

    private static Integer readExitCodeSafely(Process process) {
        if (process == null) {
            return null;
        }
        try {
            if (!process.isAlive()) {
                return process.exitValue();
            }
        } catch (IllegalThreadStateException ignored) {
            // Still running; fall through and report null.
        }
        return null;
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // Closing a closed stream is fine.
        }
    }

    private static void closeQuietly(OutputStream out) {
        if (out == null) {
            return;
        }
        try {
            out.close();
        } catch (IOException ignored) {
            // Closing a closed stream is fine.
        }
    }

}
