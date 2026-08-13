package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.autoquiz.AutoQuizDispatchPayloadDto;

/**
 * Contract for the Phase&nbsp;2D Auto Quiz backend → n8n dispatch
 * HTTP client.
 *
 * <p>Implementations MUST:</p>
 * <ul>
 *   <li>execute the HTTP POST OUTSIDE any database transaction
 *       (the dispatcher service guarantees this by design);</li>
 *   <li>NEVER log the full webhook URL when the URL may embed
 *       credentials — only the safe-redacted summary
 *       ({@link com.cmcu.itstudy.config.AutoQuizDispatchProperties#safeWebhookSummary()})
 *       may be logged;</li>
 *   <li>NEVER include credentials, JWT, service-role keys, or
 *       signed URLs in the JSON payload — the payload shape is
 *       fixed by {@link AutoQuizDispatchPayloadDto};</li>
 *   <li>translate network errors, timeouts, and non-2xx responses
 *       into the {@link DispatchOutcome} taxonomy so the dispatcher
 *       can route the row to {@code PROCESSING}, retry, or
 *       {@code FAILED} without parsing HTTP internals.</li>
 * </ul>
 */
public interface AutoQuizN8nDispatchClient {

    /**
     * POST the payload to the configured n8n webhook and report the
     * outcome. The call MUST be performed OUTSIDE any database
     * transaction.
     *
     * @param payload the JSON body to send; never null
     * @return a non-null {@link DispatchOutcome}
     */
    DispatchOutcome dispatch(AutoQuizDispatchPayloadDto payload);

    /**
     * Outcome of a single dispatch attempt.
     *
     * <p>The dispatcher service maps this enum onto the
     * {@code QuizGeneration} state machine:</p>
     * <ul>
     *   <li>{@link Result#SUCCESS} → QUEUED + token → PROCESSING</li>
     *   <li>{@link Result#TRANSIENT_FAILURE} → QUEUED retry on the
     *       next cycle</li>
     *   <li>{@link Result#PERMANENT_FAILURE} → QUEUED → FAILED (when
     *       max-attempts exhausted) or QUEUED retry (otherwise)</li>
     * </ul>
     */
    final class DispatchOutcome {

        /**
         * Coarse outcome category. {@code SUCCESS} means HTTP 2xx;
         * {@code TRANSIENT_FAILURE} covers connect / read timeouts
         * and 5xx responses; {@code PERMANENT_FAILURE} covers 4xx
         * responses (including auth failures) that should NOT be
         * retried indefinitely.
         */
        public enum Result {
            SUCCESS,
            TRANSIENT_FAILURE,
            PERMANENT_FAILURE
        }

        private final Result result;
        private final int httpStatus;
        private final String errorCode;

        public DispatchOutcome(Result result, int httpStatus,
                                String errorCode) {
            this.result = result;
            this.httpStatus = httpStatus;
            this.errorCode = errorCode;
        }

        public static DispatchOutcome success() {
            return new DispatchOutcome(Result.SUCCESS, 200, null);
        }

        public static DispatchOutcome success(int httpStatus) {
            return new DispatchOutcome(Result.SUCCESS, httpStatus, null);
        }

        public static DispatchOutcome transientFailure(String code) {
            return new DispatchOutcome(
                    Result.TRANSIENT_FAILURE, 0, code);
        }

        public static DispatchOutcome transientFailure(int httpStatus,
                                                        String code) {
            return new DispatchOutcome(
                    Result.TRANSIENT_FAILURE, httpStatus, code);
        }

        public static DispatchOutcome permanentFailure(int httpStatus,
                                                        String code) {
            return new DispatchOutcome(
                    Result.PERMANENT_FAILURE, httpStatus, code);
        }

        public Result result() {
            return result;
        }

        public int httpStatus() {
            return httpStatus;
        }

        public String errorCode() {
            return errorCode;
        }

        public boolean isSuccess() {
            return result == Result.SUCCESS;
        }

        public boolean isTransient() {
            return result == Result.TRANSIENT_FAILURE;
        }

        public boolean isPermanent() {
            return result == Result.PERMANENT_FAILURE;
        }
    }
}