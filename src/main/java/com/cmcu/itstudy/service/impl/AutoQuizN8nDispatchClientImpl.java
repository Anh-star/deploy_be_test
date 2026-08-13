package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.AutoQuizDispatchProperties;
import com.cmcu.itstudy.dto.autoquiz.AutoQuizDispatchPayloadDto;
import com.cmcu.itstudy.service.contract.AutoQuizN8nDispatchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/**
 * Default {@link AutoQuizN8nDispatchClient} implementation backed by
 * Spring's {@link RestClient} and the JDK {@link HttpClient}.
 *
 * <p>The implementation mirrors the existing
 * {@link DocumentPreviewServerUploadServiceImpl} pattern so the
 * project uses one consistent HTTP stack. The webhook URL is
 * resolved once at construction time from
 * {@link AutoQuizDispatchProperties#getWebhookUrl()} so a misconfigured
 * deployment fails fast at bean creation.</p>
 *
 * <h2>Safety guarantees</h2>
 * <ul>
 *   <li>The webhook URL is NEVER logged in full — only
 *       {@link AutoQuizDispatchProperties#safeWebhookSummary()} is
 *       ever written to logs.</li>
 *   <li>The payload is serialised through Jackson with
 *       {@link AutoQuizDispatchPayloadDto} so no field outside the
 *       published contract can leak.</li>
 *   <li>Network / read timeouts are honoured by the underlying
 *       {@link HttpClient} (connect) and the
 *       {@link JdkClientHttpRequestFactory} (read).</li>
 *   <li>HTTP error responses (4xx / 5xx) and transport failures
 *       (timeout, connection refused) are translated into the
 *       {@link AutoQuizN8nDispatchClient.DispatchOutcome} taxonomy
 *       so the dispatcher service can route the row without parsing
 *       internals.</li>
 * </ul>
 */
@Service
public class AutoQuizN8nDispatchClientImpl
        implements AutoQuizN8nDispatchClient {

    private static final Logger log =
            LoggerFactory.getLogger(AutoQuizN8nDispatchClientImpl.class);

    /**
     * Operational error codes persisted on the row when the
     * dispatcher retries or fails terminally. The codes are
     * intentionally bounded so {@code lastError} never grows
     * unbounded and never leaks the raw HTTP response body.
     */
    static final String CODE_TIMEOUT = "AUTOQUIZ_DISPATCH_TIMEOUT";
    static final String CODE_TRANSPORT = "AUTOQUIZ_DISPATCH_TRANSPORT";
    static final String CODE_4XX = "AUTOQUIZ_DISPATCH_HTTP_4XX";
    static final String CODE_5XX = "AUTOQUIZ_DISPATCH_HTTP_5XX";
    static final String CODE_UNEXPECTED = "AUTOQUIZ_DISPATCH_UNEXPECTED";

    private final AutoQuizDispatchProperties properties;
    private final RestClient restClient;
    /**
     * Cached webhook URI. {@code null} when the dispatcher is
     * disabled so the application context can boot without a
     * configured webhook URL. The URI is resolved lazily on the
     * FIRST dispatch invocation when the dispatcher is enabled.
     *
     * <p>Resolving the URI only at dispatch time (not at
     * construction time) is what allows the default
     * {@code enabled=false} deployment to start cleanly: the
     * scheduler bean is gated by
     * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}
     * and is absent in disabled mode, so {@link #dispatch} is never
     * called and the lazy URI is never queried.</p>
     */
    private final URI webhookUri;

    /**
     * Production constructor. Builds the {@link RestClient} from the
     * configured URL and timeouts.
     *
     * <p>When {@code app.auto-quiz.dispatch.enabled=false} the
     * webhook URI is NOT resolved here so a deployment without a
     * configured webhook URL can still start. The contract
     * {@code enabled=true ⇒ webhookUrl supplied} is enforced by
     * {@link AutoQuizDispatchProperties#validate()} which is called
     * once during construction and by
     * {@link AutoQuizDispatchConfiguration#validateProperties()}.</p>
     *
     * @param properties typed dispatcher configuration
     * @throws IllegalStateException when {@code enabled=true} and
     *         the webhook URL is blank/unparseable, or when the
     *         configured timeouts are invalid (see
     *         {@link AutoQuizDispatchProperties#validate()})
     */
    @Autowired
    public AutoQuizN8nDispatchClientImpl(AutoQuizDispatchProperties properties) {
        this(properties, null);
    }

    /**
     * Test-friendly constructor accepting an externally-built
     * {@link RestClient} (typically wired to a
     * {@link org.springframework.test.web.client.MockRestServiceServer}).
     */
    public AutoQuizN8nDispatchClientImpl(
            AutoQuizDispatchProperties properties,
            RestClient testClient) {
        this.properties = Objects.requireNonNull(properties, "properties");
        properties.validate();
        if (properties.isEnabled()) {
            this.webhookUri = resolveWebhookUri(properties);
        } else {
            // Disabled mode: webhook URL is irrelevant. Leave the
            // cached URI null so application context startup can
            // succeed without a configured webhook URL. The
            // scheduler bean is gated by
            // @ConditionalOnProperty(enabled=true) so dispatch() is
            // never reached in this state.
            this.webhookUri = null;
        }
        if (testClient != null) {
            this.restClient = testClient;
        } else {
            this.restClient = buildRestClient(
                    properties.getConnectTimeout(),
                    properties.getReadTimeout());
        }
    }

    @Override
    public DispatchOutcome dispatch(AutoQuizDispatchPayloadDto payload) {
        if (payload == null) {
            throw new IllegalArgumentException(
                    "payload must not be null");
        }
        if (!properties.isEnabled() || webhookUri == null) {
            // Defense-in-depth: the scheduler bean is gated by
            // @ConditionalOnProperty(enabled=true) so this branch is
            // unreachable in production. It guards against future
            // refactors that might call dispatch() directly from a
            // disabled context.
            throw new IllegalStateException(
                    "Auto Quiz dispatch is disabled; refusing to dispatch");
        }
        try {
            restClient.post()
                    .uri(webhookUri)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        String code = status >= 500 ? CODE_5XX : CODE_4XX;
                        log.warn(
                                "Auto Quiz n8n dispatch failed: category={} "
                                        + "endpoint={} generationId={} "
                                        + "httpStatus={}",
                                code,
                                properties.safeWebhookSummary(),
                                payload.getGenerationId(),
                                status);
                        if (status >= 500) {
                            // Throw a transient marker so the catch
                            // block below routes to TRANSIENT_FAILURE.
                            throw new N8nTransientHttpException(status);
                        } else {
                            throw new N8nPermanentHttpException(status);
                        }
                    })
                    .toBodilessEntity();
            log.info(
                    "Auto Quiz n8n dispatch accepted: endpoint={} "
                            + "generationId={}",
                    properties.safeWebhookSummary(),
                    payload.getGenerationId());
            return DispatchOutcome.success(200);
        } catch (N8nPermanentHttpException permanent) {
            return DispatchOutcome.permanentFailure(
                    permanent.status(), CODE_4XX);
        } catch (N8nTransientHttpException transient_) {
            return DispatchOutcome.transientFailure(
                    transient_.status(), CODE_5XX);
        } catch (ResourceAccessException timeout) {
            // Connect / read timeout → TRANSIENT_FAILURE so the
            // dispatcher retries on the next cycle.
            log.warn(
                    "Auto Quiz n8n dispatch timed out: endpoint={} "
                            + "generationId={}",
                    properties.safeWebhookSummary(),
                    payload.getGenerationId());
            return DispatchOutcome.transientFailure(CODE_TIMEOUT);
        } catch (RestClientException transport) {
            log.warn(
                    "Auto Quiz n8n dispatch transport error: endpoint={} "
                            + "generationId={}",
                    properties.safeWebhookSummary(),
                    payload.getGenerationId());
            return DispatchOutcome.transientFailure(CODE_TRANSPORT);
        } catch (RuntimeException unexpected) {
            log.warn(
                    "Auto Quiz n8n dispatch unexpected error: endpoint={} "
                            + "generationId={}",
                    properties.safeWebhookSummary(),
                    payload.getGenerationId(),
                    unexpected);
            return DispatchOutcome.transientFailure(CODE_UNEXPECTED);
        }
    }

    private static URI resolveWebhookUri(AutoQuizDispatchProperties p) {
        String url = p.getWebhookUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "AutoQuizDispatchProperties.webhookUrl must be supplied");
        }
        try {
            return URI.create(url);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "AutoQuizDispatchProperties.webhookUrl is not a "
                            + "valid URI: " + p.safeWebhookSummary(), e);
        }
    }

    private static RestClient buildRestClient(Duration connectTimeout,
                                                Duration readTimeout) {
        // Connection-level timeout lives on java.net.http.HttpClient.
        // Request-level (read) timeout lives on
        // JdkClientHttpRequestFactory.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Internal marker thrown by the {@code onStatus} handler when
     * the response carries a 5xx status. The outer catch block
     * maps it onto {@link DispatchOutcome.DispatchResult#TRANSIENT_FAILURE}.
     */
    private static final class N8nTransientHttpException
            extends org.springframework.web.client.RestClientException {
        private static final long serialVersionUID = 1L;
        private final int status;
        N8nTransientHttpException(int status) {
            super("n8n 5xx: " + status);
            this.status = status;
        }
        int status() {
            return status;
        }
    }

    /**
     * Internal marker thrown by the {@code onStatus} handler when
     * the response carries a 4xx status. The outer catch block
     * maps it onto {@link DispatchOutcome.DispatchResult#PERMANENT_FAILURE}.
     */
    private static final class N8nPermanentHttpException
            extends org.springframework.web.client.RestClientException {
        private static final long serialVersionUID = 1L;
        private final int status;
        N8nPermanentHttpException(int status) {
            super("n8n 4xx: " + status);
            this.status = status;
        }
        int status() {
            return status;
        }
    }
}