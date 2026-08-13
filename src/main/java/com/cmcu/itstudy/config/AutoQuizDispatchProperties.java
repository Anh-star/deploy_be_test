package com.cmcu.itstudy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Typed configuration for the Phase&nbsp;2D Auto Quiz backend → n8n
 * dispatch handshake.
 *
 * <p>The dispatcher is a small fixed-delay batch scheduler that claims
 * {@code QUEUED} {@code QuizGeneration} rows through an atomic
 * conditional UPDATE (assigning a {@code dispatchToken}), POSTs a
 * stable JSON payload to the configured n8n webhook, and — on a
 * 2xx response — atomically transitions the row to
 * {@code PROCESSING}.</p>
 *
 * <h2>Master switch</h2>
 * <p>The worker defaults to {@code enabled=false} so an accidental
 * deployment cannot fire HTTP traffic at a webhook. Operators are
 * expected to enable the dispatcher explicitly per environment by
 * overriding the corresponding environment variables.</p>
 *
 * <h2>Webhook URL handling</h2>
 * <ul>
 *   <li>The URL MUST come from configuration (environment variable,
 *       property, or {@code application.properties}). It MUST NOT be
 *       hard-coded in source.</li>
 *   <li>The URL MAY contain credentials when the operator chooses to
 *       use HTTP basic auth (e.g. {@code https://user:token@host/...}).
 *       Such URLs are NEVER logged. The dispatcher logs only a
 *       safe-redacted summary ({@code https://host[:port]/path}).</li>
 * </ul>
 *
 * <h2>Environment variable names</h2>
 * <p>Spring Boot relaxed binding maps each property to the following
 * uppercase env names. Operators can override defaults per environment
 * without touching {@code application.properties}:</p>
 * <ul>
 *   <li>{@code APP_AUTO_QUIZ_DISPATCH_ENABLED}</li>
 *   <li>{@code APP_AUTO_QUIZ_DISPATCH_WEBHOOK_URL}</li>
 *   <li>{@code APP_AUTO_QUIZ_DISPATCH_BATCH_SIZE}</li>
 *   <li>{@code APP_AUTO_QUIZ_DISPATCH_FIXED_DELAY_MS}</li>
 *   <li>{@code APP_AUTO_QUIZ_DISPATCH_CONNECT_TIMEOUT_MS}</li>
 *   <li>{@code APP_AUTO_QUIZ_DISPATCH_READ_TIMEOUT_MS}</li>
 *   <li>{@code APP_AUTO_QUIZ_DISPATCH_MAX_ATTEMPTS}</li>
 *   <li>{@code APP_AUTO_QUIZ_DISPATCH_LEASE_TIMEOUT_MS}</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auto-quiz.dispatch")
public class AutoQuizDispatchProperties {

    /**
     * Master switch. When {@code false} the dispatcher cycle is a
     * no-op: no claim, no HTTP call. The default value is
     * {@code false} so an accidental deployment cannot fire HTTP
     * traffic at a webhook.
     */
    private boolean enabled = false;

    /**
     * n8n webhook URL. Operators MUST supply this via
     * {@code APP_AUTO_QUIZ_DISPATCH_WEBHOOK_URL} or the corresponding
     * property. The URL MAY embed HTTP basic credentials; the
     * dispatcher never logs the user-info component.
     *
     * <p>The {@link #validate()} method rejects blank values when the
     * dispatcher is enabled so an accidental partial deployment fails
     * fast at startup.</p>
     */
    private String webhookUrl;

    /**
     * Maximum number of generations claimed per dispatcher cycle.
     * Bounded to {@code [1, 50]} by {@link #validate()} so a runaway
     * configuration cannot dispatch hundreds of webhooks per cycle.
     */
    private int batchSize = 4;

    /**
     * Fixed delay between two consecutive dispatcher cycles. The
     * default sits inside the latency target without becoming a
     * busy loop. The value is clamped to {@code [250, 30_000]} by
     * {@link #validate()}.
     */
    private long fixedDelayMs = 3_000L;

    /**
     * Connect timeout for the n8n HTTP call. Values below 250 ms
     * or above 30 seconds are rejected.
     */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /**
     * Read timeout for the n8n HTTP call. Values below 250 ms or
     * above 60 seconds are rejected.
     */
    private Duration readTimeout = Duration.ofSeconds(10);

    /**
     * Maximum number of dispatch attempts per generation before
     * the row is transitioned to {@code FAILED}. The dispatcher
     * uses {@code attempts + 1} against this threshold — a row
     * that already has {@code attempts = 3} and {@code maxAttempts = 4}
     * gets one more retry before {@code FAILED}.
     */
    private int maxAttempts = 4;

    /**
     * Stale dispatch lease timeout.
     *
     * <p>A {@code QuizGeneration} row whose {@code dispatch_token}
     * is non-null AND whose {@code dispatch_token_issued_at} is
     * older than {@code now - leaseTimeout} is considered to hold
     * a stale lease. Such a row is recovered by the dispatcher
     * cycle: the lease is cleared so a future cycle can re-claim
     * the row from scratch.</p>
     *
     * <p>This is the only crash-recovery path for the dispatch
     * handshake. It exists because the claim transaction commits
     * BEFORE the HTTP call: if the JVM crashes between the claim
     * commit and the HTTP outcome, the row would otherwise remain
     * stuck with a non-null {@code dispatch_token} that no future
     * claim can overwrite.</p>
     *
     * <p>The default of 60 seconds is intentionally larger than
     * the {@link #readTimeout} so that an in-flight HTTP request
     * (which the JVM may still be completing) cannot have its
     * lease stolen by a concurrent cycle.</p>
     *
     * <p>Only {@code QUEUED} rows are eligible for recovery; any
     * other status (PROCESSING / READY / FAILED / CANCELLED /
     * WAITING_SOURCE) is left untouched.</p>
     */
    private Duration leaseTimeout = Duration.ofSeconds(60);

    /**
     * Validate the bound property set. Invoked once at Spring bean
     * creation time by the configuration class so that an invalid
     * configuration fails fast BEFORE any dispatcher cycle is ever
     * scheduled.
     *
     * @throws IllegalStateException when any field violates the
     *         configuration contract
     */
    public void validate() {
        StringBuilder errors = new StringBuilder();

        if (enabled && (webhookUrl == null || webhookUrl.isBlank())) {
            errors.append("webhookUrl must be supplied when enabled=true; ");
        }
        if (batchSize <= 0 || batchSize > 50) {
            errors.append("batchSize must be in [1, 50]; ");
        }
        if (fixedDelayMs < 250L || fixedDelayMs > 30_000L) {
            errors.append("fixedDelayMs must be in [250, 30000]; ");
        }
        requirePositive(connectTimeout, "connectTimeout", errors);
        requirePositive(readTimeout, "readTimeout", errors);
        if (connectTimeout != null && readTimeout != null
                && readTimeout.compareTo(connectTimeout) < 0) {
            errors.append("readTimeout must be >= connectTimeout; ");
        }
        if (maxAttempts < 1 || maxAttempts > 20) {
            errors.append("maxAttempts must be in [1, 20]; ");
        }
        requirePositive(leaseTimeout, "leaseTimeout", errors);
        // Lease timeout must comfortably exceed the read timeout
        // so an in-flight HTTP request cannot have its lease
        // stolen mid-flight.
        if (leaseTimeout != null && readTimeout != null
                && leaseTimeout.compareTo(readTimeout) <= 0) {
            errors.append(
                    "leaseTimeout must be > readTimeout so an in-flight "
                            + "HTTP call cannot have its lease stolen; ");
        }

        if (errors.length() > 0) {
            throw new IllegalStateException(
                    "Invalid app.auto-quiz.dispatch configuration: "
                            + errors);
        }
    }

    private static void requirePositive(Duration d, String name,
                                        StringBuilder errors) {
        if (d == null || d.isNegative() || d.isZero()) {
            errors.append(name).append(" must be positive; ");
        }
    }

    /**
     * Safe summary of the webhook URL for log output. The user-info
     * (credentials) component is stripped, so accidental log capture
     * never leaks embedded HTTP basic auth.
     *
     * @return {@code scheme://host[:port]/path} with credentials
     *         removed, or {@code "<unset>"} when the URL is blank
     */
    public String safeWebhookSummary() {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return "<unset>";
        }
        try {
            java.net.URI u = java.net.URI.create(webhookUrl);
            StringBuilder sb = new StringBuilder();
            sb.append(u.getScheme() == null ? "http" : u.getScheme())
                    .append("://");
            // NO userInfo → credentials intentionally dropped.
            if (u.getHost() != null) {
                sb.append(u.getHost());
            }
            if (u.getPort() > 0) {
                sb.append(':').append(u.getPort());
            }
            String path = u.getPath();
            if (path != null && !path.isEmpty()) {
                sb.append(path);
            }
            return sb.toString();
        } catch (RuntimeException e) {
            // Malformed URL: return a fixed sentinel rather than the
            // raw value so we never log arbitrary input.
            return "<unparseable>";
        }
    }
}