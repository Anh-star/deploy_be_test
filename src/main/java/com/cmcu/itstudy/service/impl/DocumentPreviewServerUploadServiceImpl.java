package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.SupabaseProperties;
import com.cmcu.itstudy.handle.PreviewUploadTooLargeException;
import com.cmcu.itstudy.handle.SignedUploadTargetFailedException;
import com.cmcu.itstudy.service.contract.DocumentPreviewServerUploadService;
import com.cmcu.itstudy.service.contract.SupabaseConfigValidatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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

/**
 * Default implementation of {@link DocumentPreviewServerUploadService}.
 *
 * <p>Performs a server-side {@code POST /storage/v1/object/{bucket}/{path}}
 * upload of a generated PDF preview to Supabase Storage using the
 * service-role key configured in {@link SupabaseProperties}.</p>
 *
 * <h2>URL</h2>
 * <p>{@code {supabaseUrl}/storage/v1/object/{bucket}/{path}}</p>
 *
 * <h2>Method</h2>
 * <p>{@code POST} with {@code Content-Type: application/pdf} and the raw
 * PDF bytes as the request body.</p>
 *
 * <h2>Headers</h2>
 * <ul>
 *   <li>{@code Authorization: Bearer <service-role>}</li>
 *   <li>{@code apikey: <service-role>}</li>
 *   <li>{@code Content-Type: application/pdf}</li>
 *   <li>{@code x-upsert: false} (the worker must never silently overwrite
 *       a previously-uploaded preview it does not own)</li>
 * </ul>
 *
 * <h2>Timeouts</h2>
 * <ul>
 *   <li>connect timeout: 5 seconds</li>
 *   <li>read timeout: 30 seconds (longer than the signed-target call
 *       because the body is a real PDF)</li>
 * </ul>
 *
 * <h2>Secret handling</h2>
 * <p>This class does NOT log the service role key, apikey, Authorization
 * header, or any signed URL. The Supabase response body is NOT logged.
 * Configuration is validated up-front; no {@code null} is inserted into
 * any header.</p>
 */
@Service
public class DocumentPreviewServerUploadServiceImpl
        implements DocumentPreviewServerUploadService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DocumentPreviewServerUploadServiceImpl.class);

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Hard cap on the upload payload. The preview pipeline already
     * caps the PDF output at {@code app.preview.office.maxOutputBytes}
     * (default 25&nbsp;MiB) but the adapter asserts one more time so
     * a misconfigured caller cannot push an arbitrarily large blob at
     * Supabase.
     */
    public static final long MAX_UPLOAD_BYTES = 25L * 1024L * 1024L;

    private final SupabaseProperties properties;
    private final SupabaseConfigValidatorService configValidator;

    /**
     * Optional pre-built {@link RestClient} used during tests. When
     * non-null, the production request-factory wiring is bypassed.
     */
    private final RestClient testClient;

    @Autowired
    public DocumentPreviewServerUploadServiceImpl(
            SupabaseProperties properties,
            SupabaseConfigValidatorService configValidator) {
        this(properties, configValidator, null);
    }

    DocumentPreviewServerUploadServiceImpl(
            SupabaseProperties properties,
            SupabaseConfigValidatorService configValidator,
            RestClient testClient) {
        this.properties = properties;
        this.configValidator = configValidator;
        this.testClient = testClient;
    }

    @Override
    public void uploadPdfPreview(String bucket, String path, byte[] pdfBytes,
                                 String contentType) {
        if (bucket == null || bucket.isBlank()) {
            log.warn("Supabase preview upload failed: category=invalid-bucket");
            throw new SignedUploadTargetFailedException(
                    "Bucket must not be blank", "invalid-bucket");
        }
        if (path == null || path.isBlank()) {
            log.warn("Supabase preview upload failed: category=invalid-path");
            throw new SignedUploadTargetFailedException(
                    "Path must not be blank", "invalid-path");
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            log.warn("Supabase preview upload failed: category=empty-pdf-bytes");
            throw new SignedUploadTargetFailedException(
                    "PDF bytes must not be empty", "empty-pdf-bytes");
        }
        if (pdfBytes.length > MAX_UPLOAD_BYTES) {
            throw new PreviewUploadTooLargeException(
                    "PDF preview payload exceeds max upload size ("
                            + pdfBytes.length + " > " + MAX_UPLOAD_BYTES + ")");
        }
        String safeContentType = (contentType == null || contentType.isBlank())
                ? MediaType.APPLICATION_PDF_VALUE
                : contentType;
        // The contract pins application/pdf as the only supported
        // content type. A non-PDF value would cause Supabase to store
        // the bytes with the wrong MIME, so the adapter rejects it
        // up-front.
        if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(safeContentType)) {
            log.warn("Supabase preview upload failed: category=bad-content-type");
            throw new SignedUploadTargetFailedException(
                    "Only application/pdf is supported by this adapter",
                    "bad-content-type");
        }

        configValidator.validateSignedUploadTargetConfig(properties);

        String baseUrl = stripTrailingSlash(properties.getUrl());
        String bucketAndPath = bucket + "/" + path;
        URI endpoint = URI.create(
                baseUrl + "/storage/v1/object/" + bucketAndPath);

        RestClient client = testClient != null
                ? testClient
                : buildRestClient(baseUrl, properties.getServiceRoleKey());

        try {
            client.post()
                    .uri(endpoint)
                    .header(HttpHeaders.CONTENT_TYPE, safeContentType)
                    .header("x-upsert", "false")
                    .body(pdfBytes)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (req, res) -> {
                                int status = res.getStatusCode().value();
                                String category = status >= 500
                                        ? "http-5xx"
                                        : "http-4xx";
                                log.warn(
                                        "Supabase preview upload failed: "
                                                + "category={}",
                                        category);
                                throw new SignedUploadTargetFailedException(
                                        "Supabase refused preview upload",
                                        category);
                            })
                    .toBodilessEntity();
        } catch (SignedUploadTargetFailedException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("Supabase preview upload failed: category=timeout");
            throw new SignedUploadTargetFailedException(
                    "Supabase preview upload timed out", "timeout");
        } catch (RestClientException e) {
            log.warn("Supabase preview upload failed: category=transport");
            throw new SignedUploadTargetFailedException(
                    "Supabase preview upload failed", "transport");
        } catch (RuntimeException e) {
            log.warn("Supabase preview upload failed: category=unexpected");
            throw new SignedUploadTargetFailedException(
                    "Supabase preview upload failed", "unexpected");
        }
    }

    private RestClient buildRestClient(String baseUrl, String serviceRoleKey) {
        // Connection-level configuration lives on java.net.http.HttpClient;
        // request-level timeout configuration lives on
        // JdkClientHttpRequestFactory. The Spring Framework 7
        // JdkClientHttpRequestFactory does NOT expose a toBuilder()
        // method, so we assemble the request factory step-by-step.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + serviceRoleKey)
                .defaultHeader("apikey", serviceRoleKey)
                .requestFactory(requestFactory)
                .build();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
