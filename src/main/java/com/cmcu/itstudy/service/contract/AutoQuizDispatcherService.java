package com.cmcu.itstudy.service.contract;

import java.util.UUID;

/**
 * Contract for the Phase&nbsp;2D Auto Quiz dispatcher service.
 *
 * <p>The contract is intentionally narrow: it exposes the
 * per-cycle entry point and a safe no-op when the dispatcher is
 * disabled. The full state machine is documented on the
 * implementation class
 * {@link com.cmcu.itstudy.service.impl.AutoQuizDispatcherServiceImpl}.</p>
 */
public interface AutoQuizDispatcherService {

    /**
     * Execute a single dispatcher cycle.
     *
     * <p>A cycle:</p>
     * <ol>
     *   <li>claims up to {@code batchSize} candidates from the
     *       repository;</li>
     *   <li>for each candidate, atomically leases a fresh
     *       {@code dispatchToken} (a small {@code REQUIRES_NEW}
     *       transaction that commits BEFORE the HTTP call);</li>
     *   <li>POSTs the JSON payload to n8n OUTSIDE the database
     *       transaction;</li>
     *   <li>transitions the row to {@code PROCESSING}, retries, or
     *       {@code FAILED} according to the response.</li>
     * </ol>
     *
     * <p>The call is a no-op when the dispatcher is disabled
     * ({@code app.auto-quiz.dispatch.enabled=false}).</p>
     *
     * @return non-null {@link CycleOutcome}; the dispatcher emits
     *         counters so tests / metrics can assert on activity
     */
    CycleOutcome runCycle();

    /**
     * Coarse counters emitted by a single dispatcher cycle.
     * Returned to {@link com.cmcu.itstudy.scheduler.AutoQuizDispatcherScheduler}
     * so the caller can log a per-cycle summary without holding
     * service-level state.
     */
    final class CycleOutcome {
        private final int candidates;
        private final int claimed;
        private final int dispatched;
        private final int processing;
        private final int retry;
        private final int failed;
        private final int skipped;

        public CycleOutcome(int candidates, int claimed, int dispatched,
                             int processing, int retry, int failed,
                             int skipped) {
            this.candidates = candidates;
            this.claimed = claimed;
            this.dispatched = dispatched;
            this.processing = processing;
            this.retry = retry;
            this.failed = failed;
            this.skipped = skipped;
        }

        public int candidates() {
            return candidates;
        }

        public int claimed() {
            return claimed;
        }

        public int dispatched() {
            return dispatched;
        }

        public int processing() {
            return processing;
        }

        public int retry() {
            return retry;
        }

        public int failed() {
            return failed;
        }

        public int skipped() {
            return skipped;
        }

        @Override
        public String toString() {
            return "CycleOutcome{candidates=" + candidates
                    + ", claimed=" + claimed
                    + ", dispatched=" + dispatched
                    + ", processing=" + processing
                    + ", retry=" + retry
                    + ", failed=" + failed
                    + ", skipped=" + skipped + '}';
        }

        /**
         * Empty outcome returned by disabled / no-op cycles.
         */
        public static CycleOutcome noop() {
            return new CycleOutcome(0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Dispatcher-disabled contract hook. Used by the
     * scheduler to early-exit when {@code enabled=false}.
     *
     * @return {@code true} when the dispatcher will perform
     *         work on the next cycle; {@code false} otherwise
     */
    boolean isEnabled();

    /**
     * Diagnostic accessor exposing the lease token assigned to a
     * row in a previous cycle. Exposed for tests; production code
     * does not need this accessor.
     *
     * @param generationId id of the {@code QuizGeneration} row
     * @return the current {@code dispatchToken} lease, or
     *         {@code null} when the row is not currently leased
     */
    UUID currentLeaseTokenForTest(UUID generationId);
}