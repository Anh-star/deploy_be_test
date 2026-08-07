package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.DocumentPreviewWorkerProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Deterministic bounded exponential backoff for the Phase&nbsp;O3
 * preview worker.
 *
 * <p>The contract is:</p>
 * <pre>
 *     delay = min(retryMaxDelay,
 *                 retryBaseDelay * 2^(attemptCount - 1))
 *     nextAttemptAt = now + delay + FLOOR_PADDING
 * </pre>
 *
 * <p>The {@link #nextAttemptAt(LocalDateTime, int)} entry point takes
 * the {@code now} value computed ONCE by the worker at the start of
 * the cycle. The calculator MUST NOT independently read the application
 * {@link Clock}; doing so would break the consistent-now contract and
 * produce a {@code nextAttemptAt} that disagrees with the
 * {@code staleBefore}, {@code markReady}, {@code markRetry} and
 * {@code markDead} timestamps supplied by the same cycle.</p>
 *
 * <p>The {@link #computeDelay(int)} helper is exposed for unit tests
 * and is pure &mdash; it does not consult the clock.</p>
 */
@Component
public class DocumentPreviewBackoffCalculator {

    /**
     * Minimum padding so that the {@code nextAttemptAt} is always
     * strictly greater than {@code now} even when the computed
     * exponential delay is exactly one second.
     */
    static final Duration FLOOR_PADDING = Duration.ofSeconds(1);

    private final DocumentPreviewWorkerProperties properties;
    private final Clock clock;

    public DocumentPreviewBackoffCalculator(
            DocumentPreviewWorkerProperties properties,
            Clock clock) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Compute the next {@code nextAttemptAt} timestamp for a retried
     * artifact, using the {@code now} value supplied by the worker.
     *
     * @param now                the cycle-supplied timestamp; must not
     *                           be null and must be the SAME instant
     *                           used for {@code staleBefore} and the
     *                           guarded state updates
     * @param claimedAttemptCount the attempt count AFTER the claim
     *                            SQL incremented it (always {@code >= 1})
     * @return a {@code LocalDateTime} strictly greater than {@code now}
     */
    public LocalDateTime nextAttemptAt(LocalDateTime now,
                                       int claimedAttemptCount) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (claimedAttemptCount < 1) {
            throw new IllegalArgumentException(
                    "claimedAttemptCount must be >= 1: " + claimedAttemptCount);
        }
        Duration delay = computeDelay(claimedAttemptCount);
        LocalDateTime candidate = now.plus(delay);
        if (!candidate.isAfter(now)) {
            candidate = now.plus(FLOOR_PADDING);
        }
        return candidate;
    }

    /**
     * Compute the raw delay without applying the {@link #FLOOR_PADDING}
     * padding. Exposed for unit tests; pure &mdash; does not consult
     * the clock.
     */
    public Duration computeDelay(int claimedAttemptCount) {
        if (claimedAttemptCount < 1) {
            throw new IllegalArgumentException(
                    "claimedAttemptCount must be >= 1: " + claimedAttemptCount);
        }
        Duration base = properties.getRetryBaseDelay();
        Duration max = properties.getRetryMaxDelay();
        if (base == null || base.isNegative() || base.isZero()) {
            throw new IllegalStateException(
                    "retryBaseDelay must be positive: " + base);
        }
        if (max == null || max.isNegative() || max.isZero()) {
            throw new IllegalStateException(
                    "retryMaxDelay must be positive: " + max);
        }

        // 2^(attemptCount - 1) with overflow guard. attemptCount is
        // bounded by maxAttempts (default 5) so the loop is short,
        // but the guard is defence-in-depth.
        long multiplier = 1L;
        for (int i = 1; i < claimedAttemptCount; i++) {
            if (multiplier > Long.MAX_VALUE / 2) {
                multiplier = Long.MAX_VALUE;
                break;
            }
            multiplier <<= 1;
        }

        // Detect overflow BEFORE the multiplication so the cap, not an
        // ArithmeticException, is what callers see. The previous
        // implementation used Math.multiplyExact which threw on
        // overflow; this is now an explicit safe guard.
        long baseSeconds = base.getSeconds();
        long maxSeconds = max.getSeconds();
        long candidateSeconds;
        if (baseSeconds <= 0 || multiplier <= 0
                || baseSeconds > Long.MAX_VALUE / multiplier) {
            // Overflow would occur; honour the cap.
            candidateSeconds = maxSeconds;
        } else {
            candidateSeconds = baseSeconds * multiplier;
        }
        Duration capped = candidateSeconds <= maxSeconds
                ? Duration.ofSeconds(candidateSeconds)
                : max;
        // Ensure the result is strictly positive even when the
        // configuration yields a zero-duration (the validator already
        // rejects zero/negative durations, but this is a defence-in-depth).
        if (capped.isZero() || capped.isNegative()) {
            capped = FLOOR_PADDING;
        }
        return capped;
    }

    /**
     * @return the {@link Clock} injected at construction. Exposed so
     *         the worker scheduler can read the same clock; not used
     *         by the calculator itself.
     */
    public Clock clock() {
        return clock;
    }
}