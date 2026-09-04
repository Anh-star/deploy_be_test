package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.SupabaseProperties;
import com.cmcu.itstudy.dto.storage.SignedUploadTarget;
import com.cmcu.itstudy.dto.storage.StorageObjectInfo;
import com.cmcu.itstudy.handle.SignedUploadTargetFailedException;
import com.cmcu.itstudy.handle.StorageObjectNotFoundException;
import com.cmcu.itstudy.service.contract.SupabaseConfigValidatorService;
import com.cmcu.itstudy.service.contract.SupabaseStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Supabase implementation using {@link RestClient} with explicit
 * timeouts.
 *
 * <h2>Request contract</h2>
 * <p>Outgoing request:
 * <ul>
 *   <li>Method: POST</li>
 *   <li>Route: {@code {supabaseUrl}/storage/v1/object/upload/sign/{bucket}/{path}}</li>
 *   <li>Body: {@code "{}"} (empty JSON object)</li>
 *   <li>Headers: {@code Authorization: Bearer <service-role>},
 *       {@code apikey: <service-role>},
 *       {@code Content-Type: application/json}</li>
 * </ul>
 *
 * <h2>Response contract</h2>
 * <p>Successful Supabase response:
 * <pre>
 *     { "url": "/object/upload/sign/&lt;bucket&gt;/&lt;path&gt;?token=&lt;TOKEN&gt;" }
 * </pre>
 * <p>The {@code token} query parameter is extracted; the response body
 * itself is NOT logged.
 *
 * <p>The {@code expires_at} field, if present, is IGNORED. The StudyIT
 * pending-upload bind deadline is computed independently in the
 * orchestrator.
 *
 * <h2>Timeouts</h2>
 * <ul>
 *   <li>connect timeout: 5 seconds</li>
 *   <li>read timeout (signed-upload-target, object-info): 10 seconds</li>
 *   <li>read timeout (private object download): 30 seconds &mdash;
 *       the 6.8&nbsp;MB binary PDF download needs headroom against
 *       variable egress, and on Render Java&nbsp;17 the JDK HTTP
 *       client has a documented HTTP/2 edge case that can stall
 *       mid-body. The wider read timeout is paired with a
 *       HTTP/1.1-only download transport (see
 *       {@link #productionDownloadRequestFactory()}).</li>
 *   <li>request factory:
 *       <ul>
 *         <li>signed-upload-target, object-info &mdash;
 *             {@link JdkClientHttpRequestFactory} backed by a default
 *             {@link HttpClient} (HTTP/2 with HTTP/1.1 fallback).</li>
 *         <li>private object download &mdash;
 *             {@link JdkClientHttpRequestFactory} backed by an
 *             HTTP/1.1-only {@link HttpClient}. Forcing HTTP/1.1
 *             isolates the binary download from the JDK 17.0.x
 *             HTTP/2 framing / GOAWAY bug class (see OpenJDK
 *             JDK-8335181) that has been observed surfacing as
 *             plain {@link RestClientException} body-phase
 *             {@code IOException}s on Render.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>Secret handling</h2>
 * <p>This class does NOT log the service role key, apikey, Authorization
 * header, signed URL, or signed token. The Supabase response body is
 * NOT logged. Configuration is validated up-front; no {@code null} is
 * inserted into any header.
 */
@Service
public class SupabaseStorageServiceImpl implements SupabaseStorageService {

    /** Connect timeout for the underlying HTTP client. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Read timeout for the signed-upload-target and object-info calls. */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    /**
     * Read timeout for the private-object download call.
     *
     * <p>Wider than {@link #READ_TIMEOUT} because the binary download
     * returns up to 25&nbsp;MB of bytes (typically a 6.8&nbsp;MB PDF).
     * The wider window gives the Supabase CDN egress room to stream
     * the body without the read timeout firing while the underlying
     * ISO&nbsp;8601 content-length count is still being read.
     */
    public static final Duration DOWNLOAD_READ_TIMEOUT = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageServiceImpl.class);

    /**
     * Hard cap on the size of a private-bucket object that the preview
     * pipeline is willing to stream. The StudyIT upload pipeline caps
     * paid uploads at 25&nbsp;MB, so anything larger indicates either a
     * misconfigured object or a pending pipeline violation — either way,
     * the preview endpoint refuses to load the bytes.
     */
    public static final long PREVIEW_DOWNLOAD_MAX_BYTES = 25L * 1024L * 1024L;

    private final SupabaseProperties properties;
    private final SupabaseConfigValidatorService configValidator;
    private final ObjectMapper objectMapper;
    /**
     * Optional pre-built {@link RestClient} used during tests. When
     * non-null, the production request-factory wiring is bypassed.
     */
    private final RestClient testClient;

    /**
     * Production constructor. This is the only constructor that Spring
     * is allowed to use for dependency injection. It is explicitly
     * annotated with {@link Autowired} so that Spring does NOT confuse
     * it with the package-private test-only constructor declared below.
     *
     * <p>The constructor accepts only the production-required
     * dependencies; no test seam is exposed here.
     */
    @Autowired
    public SupabaseStorageServiceImpl(
            SupabaseProperties properties,
            SupabaseConfigValidatorService configValidator,
            ObjectMapper objectMapper) {
        this(properties, configValidator, objectMapper, null);
    }

    /**
     * Test-friendly constructor. The supplied {@code testClient} is used
     * directly by the {@link #createSignedUploadTarget(String, String)}
     * call; the production timeout factory is NOT applied. This is for
     * use with
     * {@link org.springframework.test.web.client.MockRestServiceServer}.
     *
     * <p>The constructor is package-private (NOT {@code public}) and is
     * NOT annotated with {@link Autowired}, so Spring will never select
     * it during bean construction. Production bean creation always
     * routes through the {@link Autowired}-annotated constructor above.
     */
    SupabaseStorageServiceImpl(
            SupabaseProperties properties,
            SupabaseConfigValidatorService configValidator,
            ObjectMapper objectMapper,
            RestClient testClient) {
        this.properties = properties;
        this.configValidator = configValidator;
        this.objectMapper = objectMapper;
        this.testClient = testClient;
    }

    @Override
    public SignedUploadTarget createSignedUploadTarget(String bucket, String path) {
        // Validate bucket and path before touching the config layer.
        if (bucket == null || bucket.isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Bucket must not be blank");
        }
        if (path == null || path.isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Path must not be blank");
        }

        // Validate config up-front. The validator never returns null and
        // never echoes secret values.
        configValidator.validateSignedUploadTargetConfig(properties);

        // Build the URI by appending the bucket and path segments to the
        // Supabase URL. The path is server-generated and validated; we
        // intentionally do NOT encode the slashes inside the path so the
        // Supabase route matches its wildcard.
        String baseUrl = stripTrailingSlash(properties.getUrl());
        String bucketAndPath = bucket + "/" + path;
        URI endpoint = URI.create(baseUrl + "/storage/v1/object/upload/sign/" + bucketAndPath);

        String requestBody = "{}";

        RestClient client = testClient != null
                ? testClient
                : buildRestClient(baseUrl, properties.getServiceRoleKey());

        try {
            String rawResponse = client.post()
                    .uri(endpoint)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(org.springframework.http.HttpStatusCode::isError,
                            (req, res) -> {
                                int status = res.getStatusCode().value();
                                log.warn(
                                        "Supabase signed-upload-target refused: status={}",
                                        status);
                                throw new SignedUploadTargetFailedException(
                                        "Supabase refused signed-upload-target (status "
                                                + status + ")");
                            })
                    .body(String.class);

            return parseResponse(rawResponse);
        } catch (SignedUploadTargetFailedException e) {
            throw e;
        } catch (ResourceAccessException e) {
            // Includes timeout (ConnectTimeoutException, ReadTimeoutException).
            log.warn("Supabase signed-upload-target timed out");
            throw new SignedUploadTargetFailedException(
                    "Supabase signed-upload-target timed out", e);
        } catch (RestClientException e) {
            log.warn("Supabase signed-upload-target failed");
            throw new SignedUploadTargetFailedException(
                    "Supabase signed-upload-target failed", e);
        } catch (RuntimeException e) {
            log.warn("Supabase signed-upload-target unexpected failure");
            throw new SignedUploadTargetFailedException(
                    "Supabase signed-upload-target failed", e);
        }
    }

    /**
     * Fetch authoritative object metadata via the official Supabase
     * Storage object-info endpoint
     * ({@code GET /storage/v1/object/info/{bucket}/{path}}).
     *
     * <p>Wire contract (source-proven):
     * <ul>
     *   <li>Method: {@code GET}</li>
     *   <li>Route: {@code {supabaseUrl}/storage/v1/object/info/{bucket}/{path}}</li>
     *   <li>Headers: {@code Authorization: Bearer <service-role>},
     *       {@code apikey: <service-role>}</li>
     *   <li>Body: none</li>
     *   <li>Response (200, JSON, {@code FileObjectV2} per
     *       {@code supabase/storage-js}): camelCase fields with system
     *       metadata flattened to top-level — {@code size},
     *       {@code contentType}, {@code lastModified}, {@code etag},
     *       {@code id}, {@code version}, {@code name}, {@code bucketId},
     *       {@code createdAt}, etc. Only the binder-consumed fields
     *       are propagated.</li>
     *   <li>Response 404 → mapped to {@link StorageObjectNotFoundException}.</li>
     *   <li>Response 401 / 403 → mapped to
     *       {@link SignedUploadTargetFailedException} with a safe
     *       generic message (status code is logged but never echoed
     *       back).</li>
     *   <li>Timeout / network failure → mapped to
     *       {@link SignedUploadTargetFailedException} with a safe
     *       category. Raw response bodies, paths, signed tokens,
     *       service role keys, and {@code apikey} values are NEVER
     *       logged or surfaced.</li>
     * </ul>
     *
     * <p>The call uses the same backend credentials as
     * {@link #createSignedUploadTarget}; bucket and path are
     * server-resolved from {@link com.cmcu.itstudy.entity.PendingStorageUpload}.
     */
    @Override
    public StorageObjectInfo getObjectInfo(String bucket, String path) {
        if (bucket == null || bucket.isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Bucket must not be blank");
        }
        if (path == null || path.isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Path must not be blank");
        }

        configValidator.validateSignedUploadTargetConfig(properties);

        String baseUrl = stripTrailingSlash(properties.getUrl());
        String bucketAndPath = bucket + "/" + path;
        URI endpoint = URI.create(baseUrl + "/storage/v1/object/info/" + bucketAndPath);

        RestClient client = testClient != null
                ? testClient
                : buildRestClient(baseUrl, properties.getServiceRoleKey());

        try {
            String rawResponse = client.get()
                    .uri(endpoint)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (req, res) -> {
                                int status = res.getStatusCode().value();
                                log.warn(
                                        "Supabase object-info refused: status={}",
                                        status);
                                if (status == 404) {
                                    throw new StorageObjectNotFoundException(
                                            "Supabase object not found");
                                }
                                throw new SignedUploadTargetFailedException(
                                        "Supabase refused object-info (status "
                                                + status + ")");
                            })
                    .body(String.class);

            return parseObjectInfo(rawResponse);
        } catch (StorageObjectNotFoundException e) {
            throw e;
        } catch (SignedUploadTargetFailedException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("Supabase object-info timed out");
            throw new SignedUploadTargetFailedException(
                    "Supabase object-info timed out", e);
        } catch (RestClientException e) {
            log.warn("Supabase object-info failed");
            throw new SignedUploadTargetFailedException(
                    "Supabase object-info failed", e);
        } catch (RuntimeException e) {
            log.warn("Supabase object-info unexpected failure");
            throw new SignedUploadTargetFailedException(
                    "Supabase object-info failed", e);
        }
    }

    /**
     * Parse the {@code FileObjectV2} response from the Supabase
     * object-info endpoint.
     *
     * <p>The response body is NEVER logged. The parser only propagates
     * the binder-consumed fields ({@code size}, {@code content_type});
     * {@code last_modified} and {@code etag} are surfaced when present
     * so callers can optionally use them, otherwise {@code null}.
     *
     * <p>Wire field names — source-proven from the raw REST endpoint
     * {@code GET /storage/v1/object/info/{bucket}/{path}} — are
     * snake_case:
     * <ul>
     *   <li>{@code size} → {@link StorageObjectInfo#sizeBytes()}</li>
     *   <li>{@code content_type} → {@link StorageObjectInfo#contentType()}</li>
     *   <li>{@code last_modified} → {@link StorageObjectInfo#lastModified()}</li>
     *   <li>{@code etag} → {@link StorageObjectInfo#etag()}</li>
     * </ul>
     *
     * <p>The parser explicitly reads {@code content_type} (snake_case).
     * A {@code contentType} (camelCase) fallback is honoured only for
     * backward compatibility — raw fixtures MUST use the snake_case
     * names.
     *
     * <p>Parsing never echoes the raw JSON or any token back through
     * the exception message.
     */
    private StorageObjectInfo parseObjectInfo(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new StorageObjectNotFoundException(
                    "Supabase returned an empty object-info response");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new SignedUploadTargetFailedException(
                    "Failed to parse Supabase object-info response");
        }
        JsonNode sizeNode = root.get("size");
        if (sizeNode == null || !sizeNode.isNumber()) {
            throw new SignedUploadTargetFailedException(
                    "Supabase object-info missing size field");
        }
        long size = sizeNode.asLong();
        // Source-proven raw REST field name is snake_case. Accept the
        // camelCase variant as a backward-compat fallback only.
        String contentType = root.hasNonNull("content_type")
                ? root.get("content_type").asText()
                : (root.hasNonNull("contentType")
                        ? root.get("contentType").asText()
                        : null);
        String etag = root.hasNonNull("etag") ? root.get("etag").asText() : null;
        LocalDateTime lastModified = parseLastModified(root);
        return new StorageObjectInfo(size, contentType, etag, lastModified);
    }

    private static LocalDateTime parseLastModified(JsonNode root) {
        // Source-proven raw REST field name is snake_case. Accept the
        // camelCase variant as a backward-compat fallback only.
        JsonNode node = root.hasNonNull("last_modified")
                ? root.get("last_modified")
                : (root.hasNonNull("lastModified")
                        ? root.get("lastModified")
                        : null);
        if (node == null) {
            return null;
        }
        String raw = node.asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // Supabase returns ISO-8601 with offset, e.g. "2024-01-01T12:00:00.000Z".
            // Convert to LocalDateTime in the JVM-default zone, matching the
            // StudyIT clock strategy documented in ApplicationClockConfig.
            OffsetDateTime odt = OffsetDateTime.parse(raw);
            return odt.toLocalDateTime();
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(raw);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private RestClient buildRestClient(String baseUrl, String serviceRoleKey) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(productionRequestFactory());
        return builder.build();
    }

    /**
     * Build a {@link RestClient} dedicated to the binary
     * private-object download call.
     *
     * <p>This client uses an HTTP/1.1-only JDK
     * {@link HttpClient} and a wider read timeout than the
     * shared factory. It is intentionally separate from
     * {@link #buildRestClient(String, String)} so the
     * signed-upload-target and object-info transports are
     * unaffected by the protocol-version change.
     *
     * @param baseUrl canonical Supabase project URL
     * @param serviceRoleKey service-role JWT (never logged)
     * @return the configured {@link RestClient}
     */
    private RestClient buildDownloadRestClient(
            String baseUrl, String serviceRoleKey) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(productionDownloadRequestFactory());
        return builder.build();
    }

    private static ClientHttpRequestFactory productionRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /**
     * Build a request factory dedicated to the private-object
     * binary download.
     *
     * <p>Two changes versus {@link #productionRequestFactory()}:
     * <ul>
     *   <li>{@link HttpClient.Version#HTTP_1_1} is forced. The
     *       default JDK 17.0.x HTTP/2 client has been observed
     *       to hit OpenJDK JDK-8335181 on Render and Surface
     *       mid-body {@code IOException}s (GOAWAY handling)
     *       that Spring's {@code DefaultRestClient} wraps as a
     *       plain {@link RestClientException} with the
     *       message "Error while extracting response for
     *       type [byte&#x5b;&#x5d;] and content type
     *       [application/pdf]". Forcing HTTP/1.1 sidesteps
     *       that bug class entirely. The shared
     *       signed-upload-target / object-info transport is
     *       unaffected because it returns tiny JSON bodies
     *       that are not impacted by the same JDK HTTP/2
     *       framing issue.</li>
     *   <li>Read timeout is bumped to
     *       {@link #DOWNLOAD_READ_TIMEOUT} (30&nbsp;s) so the
     *       6.8&nbsp;MB PDF body has room to stream even on
     *       variable egress.</li>
     * </ul>
     */
    private static ClientHttpRequestFactory productionDownloadRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(DOWNLOAD_READ_TIMEOUT);
        return factory;
    }

    /**
     * Parse the raw Supabase response and extract the signed token.
     *
     * <p>Expected response body:
     * <pre>
     *     { "url": "/object/upload/sign/&lt;bucket&gt;/&lt;path&gt;?token=&lt;TOKEN&gt;" }
     * </pre>
     *
     * <p>The response body is NEVER logged; the parsed token is the only
     * field surfaced.
     */
    private SignedUploadTarget parseResponse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Supabase returned an empty response");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new SignedUploadTargetFailedException(
                    "Failed to parse Supabase response");
        }
        JsonNode urlNode = root.get("url");
        if (urlNode == null || urlNode.asText().isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Supabase response missing url field");
        }
        String url = urlNode.asText();
        String token = extractTokenFromUrl(url);
        if (token == null || token.isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Supabase response missing token query parameter");
        }
        return new SignedUploadTarget(token);
    }

    /**
     * Parse the {@code url} field and extract the {@code token} query
     * parameter. The raw URL is never logged.
     */
    private String extractTokenFromUrl(String url) {
        int qmark = url.indexOf('?');
        if (qmark < 0 || qmark == url.length() - 1) {
            return null;
        }
        String query = url.substring(qmark + 1);
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            } else {
                String key = URLDecoder.decode(
                        pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(
                        pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        String token = params.get("token");
        if (token == null) {
            return null;
        }
        return token.trim();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    /**
     * Download the raw bytes of a private-bucket object via the
     * {@code GET /storage/v1/object/{bucket}/{path}} endpoint.
     *
     * <p>Wire contract (source-consistent with the rest of this service):
     * <ul>
     *   <li>Method: {@code GET}</li>
     *   <li>Route: {@code {supabaseUrl}/storage/v1/object/{bucket}/{path}}</li>
     *   <li>Headers: {@code Authorization: Bearer <service-role>},
     *       {@code apikey: <service-role>}</li>
     *   <li>Body: none</li>
     *   <li>Response 200 → raw bytes (bounded by {@link #PREVIEW_DOWNLOAD_MAX_BYTES})</li>
     *   <li>Response 404 → mapped to {@link StorageObjectNotFoundException}.</li>
     *   <li>Response 401 / 403 → mapped to
     *       {@link SignedUploadTargetFailedException} with a safe
     *       generic message (status code is logged but never echoed
     *       back).</li>
     *   <li>Timeout / network failure → mapped to
     *       {@link SignedUploadTargetFailedException} with a safe
     *       category. Raw response bodies, paths, signed tokens,
     *       service role keys, and {@code apikey} values are NEVER
     *       logged or surfaced.</li>
     *   <li>Response body &gt; {@code 25 MB} → mapped to
     *       {@link com.cmcu.itstudy.handle.PreviewFileTooLargeException}.</li>
     * </ul>
     *
     * <p>The bytes are accumulated into a bounded buffer rather than
     * written to disk so the preview pipeline never leaves a temp file
     * lying around in the JVM working directory.
     *
     * <h3>Transport</h3>
     * <p>This call uses a dedicated HTTP/1.1-only
     * {@link ClientHttpRequestFactory} &mdash; see
     * {@link #productionDownloadRequestFactory()}. The shared
     * signed-upload-target / object-info transport is unaffected.
     *
     * <h3>Diagnostic logging</h3>
     * <p>On failure, the class name of the thrown
     * {@link RestClientException} (or its parent) and the class
     * names of up to eight deep causes are logged via
     * {@link #logDownloadTransportChain(Throwable, String)}. This
     * breadcrumb is essential for Render-side debugging because
     * the JDK 17.0.x HTTP/2 client has been observed to surface
     * a body-phase {@code IOException} as a plain
     * {@link RestClientException} with the generic message
     * "Error while extracting response for type [byte&#x5b;&#x5d;]
     * and content type [application/pdf]", which is otherwise
     * indistinguishable from any other REST failure.
     *
     * <p>Logged values NEVER include the service role key, the
     * {@code Authorization} header, the {@code apikey} header,
     * any signed token, the complete storage path, the
     * bucket, or the response body.
     */
    @Override
    public byte[] downloadPrivateObject(String bucket, String path) {
        if (bucket == null || bucket.isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Bucket must not be blank");
        }
        if (path == null || path.isBlank()) {
            throw new SignedUploadTargetFailedException(
                    "Path must not be blank");
        }

        configValidator.validateSignedUploadTargetConfig(properties);

        String baseUrl = stripTrailingSlash(properties.getUrl());
        String bucketAndPath = bucket + "/" + path;
        URI endpoint = URI.create(baseUrl + "/storage/v1/object/" + bucketAndPath);

        RestClient client = testClient != null
                ? testClient
                : buildDownloadRestClient(baseUrl, properties.getServiceRoleKey());

        try {
            byte[] payload = client.get()
                    .uri(endpoint)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (req, res) -> {
                                int status = res.getStatusCode().value();
                                log.warn(
                                        "Supabase private download refused: status={}",
                                        status);
                                if (status == 404) {
                                    throw new StorageObjectNotFoundException(
                                            "Supabase object not found");
                                }
                                throw new SignedUploadTargetFailedException(
                                        "Supabase refused private download (status "
                                                + status + ")");
                            })
                    .body(byte[].class);

            if (payload == null || payload.length == 0) {
                throw new StorageObjectNotFoundException(
                        "Supabase returned an empty private download response");
            }
            if (payload.length > PREVIEW_DOWNLOAD_MAX_BYTES) {
                log.warn("Supabase private download exceeded preview size cap");
                throw new com.cmcu.itstudy.handle.PreviewFileTooLargeException(
                        "Private object exceeds preview size cap");
            }
            return payload;
        } catch (StorageObjectNotFoundException e) {
            throw e;
        } catch (com.cmcu.itstudy.handle.PreviewFileTooLargeException e) {
            throw e;
        } catch (SignedUploadTargetFailedException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("Supabase private download timed out");
            logDownloadTransportChain(e, "timeout");
            throw new SignedUploadTargetFailedException(
                    "Supabase private download timed out", e);
        } catch (RestClientException e) {
            log.warn("Supabase private download failed");
            logDownloadTransportChain(e, "rest");
            throw new SignedUploadTargetFailedException(
                    "Supabase private download failed", e);
        } catch (RuntimeException e) {
            log.warn("Supabase private download unexpected failure");
            logDownloadTransportChain(e, "unexpected");
            throw new SignedUploadTargetFailedException(
                    "Supabase private download failed", e);
        }
    }

    /**
     * Log the class names of the failure and up to eight nested
     * causes. The category is a short, non-secret descriptor
     * (for example {@code "timeout"}, {@code "rest"},
     * {@code "unexpected"}).
     *
     * <p>This method NEVER logs the service role key, the
     * {@code Authorization} header, the {@code apikey} header,
     * any signed token, the complete storage path, the bucket,
     * or the response body. Only exception class names &mdash;
     * not exception messages &mdash; are echoed, so a Render
     * operator can see whether the failure is e.g. a
     * {@code java.io.IOException} wrapped by a
     * {@code RestClientException} without exposing any
     * Supabase payload.
     *
     * @param throwable the top-level failure (never {@code null})
     * @param category safe transport classifier
     */
    private static void logDownloadTransportChain(
            Throwable throwable, String category) {
        if (throwable == null) {
            return;
        }
        Throwable cursor = throwable;
        int depth = 0;
        int maxDepth = 8;
        while (cursor != null && depth < maxDepth) {
            log.warn(
                    "Supabase private download transport chain: "
                            + "category={} depth={} class={}",
                    category,
                    depth,
                    cursor.getClass().getName());
            cursor = cursor.getCause();
            depth++;
        }
    }

    @Override
    public void deleteObject(String bucket, String path) {
        if (bucket == null || bucket.isBlank() || path == null || path.isBlank()) {
            return;
        }
        String baseUrl = properties.getUrl();
        String bucketAndPath = bucket + "/" + path;
        URI endpoint = URI.create(baseUrl + "/storage/v1/object/" + bucketAndPath);

        RestClient client = testClient != null
                ? testClient
                : buildRestClient(baseUrl, properties.getServiceRoleKey());

        try {
            client.delete()
                    .uri(endpoint)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Supabase storage deleteObject completed for bucket={}, path={}", bucket, path);
        } catch (Exception e) {
            log.warn("Supabase storage deleteObject failed for bucket={}, path={}: {}", bucket, path, e.getMessage());
        }
    }
}
