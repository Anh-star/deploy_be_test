package com.cmcu.itstudy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Configuration skeleton for the Office (DOC / DOCX) preview converter
 * introduced in Phase&nbsp;O1.
 *
 * <p>This class only binds deployment environment variables to typed
 * fields. It does NOT start LibreOffice, does NOT contact Supabase, and
 * does NOT expose any secret value.</p>
 *
 * <h2>Environment variable names</h2>
 * <p>Spring Boot relaxed binding maps each property to the following
 * uppercase env names. The deployment contract documents these so
 * operators can override defaults per environment without touching
 * {@code application.properties}:</p>
 * <ul>
 *   <li>{@code APP_PREVIEW_OFFICE_SOFFICE_EXECUTABLE}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_CONVERSION_TIMEOUT}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_PROCESS_TERMINATION_GRACE_PERIOD}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_PROCESS_FORCED_TERMINATION_TIMEOUT}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_STREAM_DRAIN_TIMEOUT}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_SEMAPHORE_WAIT_TIMEOUT}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_MAX_CONCURRENT_CONVERSIONS}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_MAX_INPUT_BYTES}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_MAX_OUTPUT_BYTES}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_TEMP_ROOT}</li>
 *   <li>{@code APP_PREVIEW_OFFICE_DIAGNOSTIC_CAPTURE_BYTES}</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.preview.office")
public class OfficePreviewProperties {

    /**
     * LibreOffice executable name. Resolved through the inherited
     * {@code PATH} by the OS when invoked via {@link ProcessBuilder}.
     * Operators can override per deployment environment.
     */
    private String sofficeExecutable = "soffice";

    /**
     * Wall-clock budget for a single DOC / DOCX to PDF conversion.
     * The runner triggers graceful termination after this duration.
     *
     * <p>Default 90 seconds. The previous 30-second default was tuned
     * for small DOCX files; large DOCX (≈ 5–6 MB, ≈ 100+ pages) can
     * exceed 30 seconds on a cold-start LibreOffice. Operators can
     * override per environment via the env name
     * {@code APP_PREVIEW_OFFICE_CONVERSION_TIMEOUT}. The validate()
     * bound is {@code >= 1s} to prevent misconfiguration from
     * disabling the timeout entirely.</p>
     */
    private Duration conversionTimeout = Duration.ofSeconds(90);

    /**
     * Grace period given to a process tree after {@code destroy()} is
     * dispatched. After this window the runner escalates to
     * {@code destroyForcibly()}.
     */
    private Duration processTerminationGracePeriod = Duration.ofSeconds(2);

    /**
     * Maximum wait for the process tree after forced termination has
     * been dispatched. The runner MUST NOT wait forever even on a
     * stuck descendant.
     */
    private Duration processForcedTerminationTimeout = Duration.ofSeconds(5);

    /**
     * Bounded wait for stream consumers to drain after the process has
     * exited or been terminated. Used by {@code SafeProcessRunner} to
     * detect stuck readers and force-cancel their futures.
     */
    private Duration streamDrainTimeout = Duration.ofSeconds(2);

    /**
     * Maximum time the LibreOffice converter waits to acquire the
     * concurrency semaphore. Exceeding this budget surfaces a typed
     * retryable exception so Phase&nbsp;O3 can map it to RETRY.
     */
    private Duration semaphoreWaitTimeout = Duration.ofSeconds(10);

    /**
     * Phase 7B — tightened the default from {@code 2} to {@code 1} so a
     * DOC/DOCX FULL conversion can run inside the Render Free 512 MB
     * cgroup without OOM-killing the JVM. Each LibreOffice child
     * process can hold 100–200 MB of native memory on a complex DOCX;
     * running two in parallel would push the working set over the
     * cgroup limit and force a container restart, leaving the
     * PROCESSING row stranded (stale-PROCESSING reclaim picks it up
     * on the next cycle, but the user-facing OOM restart remains).
     *
     * <p>The cap is enforced by a {@link Semaphore} inside
     * {@code LibreOfficeDocumentConverter}; each conversion acquires a
     * permit before any temporary directory is created and releases it
     * in the same {@code finally} block that owns the temporary
     * directories, so permits are never leaked on retryable failure,
     * interruption, or shutdown.</p>
     *
     * <p>Operators that deploy to a larger memory budget (>= 2 GB)
     * can override the default per environment via the env name
     * {@code APP_PREVIEW_OFFICE_MAX_CONCURRENT_CONVERSIONS}.</p>
     */
    private int maxConcurrentConversions = 1;

    /**
     * Maximum accepted Office input size in bytes. Inputs that exceed
     * this cap never enter LibreOffice and the conversion surfaces a
     * typed terminal exception.
     */
    private long maxInputBytes = 25L * 1024L * 1024L;

    /**
     * Maximum accepted PDF output size in bytes. Outputs larger than
     * this cap are rejected after PDF validation.
     */
    private long maxOutputBytes = 25L * 1024L * 1024L;

    /**
     * Root directory under which the converter creates the isolated
     * input / output / LibreOffice profile directories. Defaults to the
     * JVM temp directory. Operators may override to a dedicated fast
     * disk (for example {@code /var/tmp/preview} on Linux). The value
     * is never logged or echoed in HTTP responses.
     */
    private String tempRoot = System.getProperty("java.io.tmpdir");

    /**
     * Maximum number of bytes captured from each of the LibreOffice
     * stdout and stderr streams for diagnostic purposes. Anything
     * beyond the cap is drained into a black-hole sink so the OS pipe
     * never fills and the process never deadlocks.
     */
    private int diagnosticCaptureBytes = 1024;

    /**
     * Resolved {@link Path} for {@link #tempRoot}. Computed lazily.
     */
    public Path resolvedTempRoot() {
        return Paths.get(tempRoot == null || tempRoot.isBlank()
                ? System.getProperty("java.io.tmpdir")
                : tempRoot);
    }

    /**
     * Validate the bound property set. Invoked once at Spring bean
     * creation time by {@code OfficePreviewConfiguration} so that an
     * invalid configuration fails fast and never produces a
     * per-document terminal exception.
     *
     * @throws IllegalStateException when any field violates the O1
     *         configuration contract
     */
    public void validate() {
        StringBuilder errors = new StringBuilder();

        if (sofficeExecutable == null || sofficeExecutable.isBlank()) {
            errors.append("sofficeExecutable must not be blank; ");
        }
        requirePositive(conversionTimeout, "conversionTimeout", errors);
        requireNonNegative(processTerminationGracePeriod,
                "processTerminationGracePeriod", errors);
        requireNonNegative(processForcedTerminationTimeout,
                "processForcedTerminationTimeout", errors);
        requireNonNegative(streamDrainTimeout, "streamDrainTimeout", errors);
        requirePositive(semaphoreWaitTimeout, "semaphoreWaitTimeout", errors);
        if (maxConcurrentConversions < 1) {
            errors.append("maxConcurrentConversions must be >= 1; ");
        }
        if (maxInputBytes <= 0) {
            errors.append("maxInputBytes must be > 0; ");
        }
        if (maxOutputBytes <= 0) {
            errors.append("maxOutputBytes must be > 0; ");
        }
        if (diagnosticCaptureBytes <= 0 || diagnosticCaptureBytes > 1_048_576) {
            errors.append("diagnosticCaptureBytes must be in (0, 1MiB]; ");
        }
        if (tempRoot == null || tempRoot.isBlank()) {
            errors.append("tempRoot must not be blank; ");
        }
        if (errors.length() > 0) {
            throw new IllegalStateException(
                    "Invalid app.preview.office configuration: " + errors);
        }
    }

    private static void requirePositive(Duration d, String name,
                                        StringBuilder errors) {
        if (d == null || d.isNegative() || d.isZero()) {
            errors.append(name).append(" must be positive; ");
        }
    }

    private static void requireNonNegative(Duration d, String name,
                                          StringBuilder errors) {
        if (d == null || d.isNegative()) {
            errors.append(name).append(" must be non-negative; ");
        }
    }
}
