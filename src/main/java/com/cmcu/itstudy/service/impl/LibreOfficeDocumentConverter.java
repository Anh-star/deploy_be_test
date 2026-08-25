package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.OfficePreviewProperties;
import com.cmcu.itstudy.dto.office.OfficeConversionRequest;
import com.cmcu.itstudy.dto.office.OfficeConversionResult;
import com.cmcu.itstudy.dto.office.ProcessExecutionResult;
import com.cmcu.itstudy.dto.office.ProcessRunOptions;
import com.cmcu.itstudy.dto.office.ProcessTerminationReason;
import com.cmcu.itstudy.handle.OfficeConversionConfigurationException;
import com.cmcu.itstudy.handle.OfficeConversionInputTooLargeException;
import com.cmcu.itstudy.handle.OfficeConversionInterruptedException;
import com.cmcu.itstudy.handle.OfficeConversionInvalidOutputException;
import com.cmcu.itstudy.handle.OfficeConversionIoException;
import com.cmcu.itstudy.handle.OfficeConversionOutputTooLargeException;
import com.cmcu.itstudy.handle.OfficeConversionProcessException;
import com.cmcu.itstudy.handle.OfficeConversionStartupException;
import com.cmcu.itstudy.handle.OfficeConversionTimeoutException;
import com.cmcu.itstudy.handle.OfficeConversionUnsupportedFormatException;
import com.cmcu.itstudy.service.contract.OfficeDocumentConverter;
import com.cmcu.itstudy.service.contract.OfficePdfValidationService;
import com.cmcu.itstudy.service.contract.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Implementation of {@link OfficeDocumentConverter} that runs
 * LibreOffice in headless mode with an isolated user profile.
 *
 * <h2>Resource ownership</h2>
 * <p>This class is the SOLE owner of:</p>
 * <ul>
 *   <li>the LibreOffice concurrency semaphore;</li>
 *   <li>the input / output / LibreOffice profile directories;</li>
 *   <li>the temporary input file;</li>
 *   <li>the resulting PDF bytes (validated via
 *       {@link OfficePdfValidationService}).</li>
 * </ul>
 *
 * <p>The semaphore is released exactly once per invocation. The
 * {@link SafeProcessRunner} implementation never sees the semaphore
 * and never sees any temporary directory.</p>
 *
 * <h2>Cleanup contract</h2>
 * <p>All temporary directories are deleted in the same {@code finally}
 * block that owns the semaphore. Cleanup failures NEVER mask the
 * primary conversion failure: they only emit a safe bounded warning
 * with a short correlation id.</p>
 */
@Service
public class LibreOfficeDocumentConverter implements OfficeDocumentConverter {

    private static final Logger log = LoggerFactory.getLogger(LibreOfficeDocumentConverter.class);

    private final OfficePreviewProperties properties;
    private final ProcessRunner processRunner;
    private final OfficePdfValidationService validationService;
    private final Semaphore semaphore;

    public LibreOfficeDocumentConverter(OfficePreviewProperties properties,
                                        ProcessRunner processRunner,
                                        OfficePdfValidationService validationService) {
        this.properties = properties;
        this.processRunner = processRunner;
        this.validationService = validationService;
        int permits = Math.max(1, properties.getMaxConcurrentConversions());
        this.semaphore = new Semaphore(permits);
    }

    @Override
    public OfficeConversionResult convert(OfficeConversionRequest request) {
        if (request == null) {
            throw new OfficeConversionUnsupportedFormatException(
                    "Conversion request is null");
        }
        request.enforceMaxInputBytes(properties.getMaxInputBytes());

        boolean acquired = false;
        Path inputDir = null;
        Path outputDir = null;
        Path profileDir = null;
        Instant start = Instant.now();
        Instant semaphoreAcquiredAt = null;
        Instant tempReadyAt = null;
        Instant processStartAt = null;
        Instant processDoneAt = null;
        Instant pdfLocatedAt = null;
        String correlation = safeCorrelationId(request.correlationId());

        try {
            // Semaphore acquisition is performed in a dedicated helper
            // so an InterruptedException during tryAcquire never falls
            // into the IO handler. After this call returns normally,
            // `acquired == true` and the local resource references are
            // still null until the temp directories are created below.
            acquired = acquirePermit();
            semaphoreAcquiredAt = Instant.now();

            Path tempRoot = properties.resolvedTempRoot();
            Files.createDirectories(tempRoot);

            inputDir = Files.createTempDirectory(tempRoot, "lo-in-");
            outputDir = Files.createTempDirectory(tempRoot, "lo-out-");
            profileDir = Files.createTempDirectory(tempRoot, "lo-profile-");
            tempReadyAt = Instant.now();

            String inputFileName = UUID.randomUUID() + "." + request.extension();
            Path inputFile = inputDir.resolve(inputFileName);
            Files.write(inputFile, request.bytes());

            List<String> argv = buildArgumentList(
                    properties.getSofficeExecutable(),
                    profileDir,
                    outputDir,
                    inputFile);

            ProcessRunOptions options = new ProcessRunOptions(
                    properties.getConversionTimeout(),
                    properties.getProcessTerminationGracePeriod(),
                    properties.getProcessForcedTerminationTimeout(),
                    properties.getStreamDrainTimeout(),
                    properties.getDiagnosticCaptureBytes(),
                    properties.getDiagnosticCaptureBytes(),
                    null);

            processStartAt = Instant.now();
            ProcessExecutionResult runResult = processRunner.run(argv, options);
            processDoneAt = Instant.now();
            logSafeRunResult(correlation, request.fileType(), runResult);

            if (runResult.timedOut()
                    || runResult.terminationReason() == ProcessTerminationReason.TIMEOUT) {
                // The TIMEOUT retry budget (max 1 retry, then DEAD)
                // is enforced above this layer by the
                // DocumentPreviewFailureClassifier using the
                // persisted attemptCount. The converter is only
                // responsible for surfacing the typed timeout
                // exception.
                throw new OfficeConversionTimeoutException(
                        "LibreOffice timed out after "
                                + options.timeout().toMillis() + "ms");
            }

            if (runResult.terminationReason() == ProcessTerminationReason.STARTUP_FAILURE) {
                throw new OfficeConversionStartupException(
                        "LibreOffice could not be started (executable="
                                + properties.getSofficeExecutable() + ")");
            }

            if (runResult.terminationReason() == ProcessTerminationReason.INTERRUPTED) {
                // The runner normally throws OfficeConversionInterruptedException
                // for a real process-wait interruption. Reaching this branch
                // means a malformed or mocked result declared INTERRUPTED even
                // though run() did not throw. Surface it through the same
                // typed interruption path so the caller sees a uniform
                // OfficeConversionInterruptedException.
                throw new OfficeConversionInterruptedException(
                        "Process runner reported interrupted execution",
                        null);
            }

            // A non-zero exit OR an unobservable exit code is a
            // RETRYABLE process outcome. The runner does not classify
            // a document as invalid based on stderr text. Only the
            // deterministic PDF validation below may surface a
            // terminal OfficeConversionInvalidOutputException.
            Integer exit = runResult.exitCode();
            if (exit == null) {
                throw new OfficeConversionProcessException("LO_NO_EXIT",
                        "LibreOffice exit code could not be observed");
            }
            if (exit != 0) {
                throw new OfficeConversionProcessException("LO_NONZERO",
                        "LibreOffice exited with non-zero code " + exit);
            }

            Path pdf = locateSinglePdf(outputDir);
            pdfLocatedAt = Instant.now();
            int pageCount = validationService.validateAndCountPages(pdf);

            byte[] bytes = Files.readAllBytes(pdf);
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new OfficeConversionOutputTooLargeException(
                        "PDF output size " + bytes.length
                                + " exceeds maximum " + properties.getMaxOutputBytes());
            }

            Duration elapsed = Duration.between(start, Instant.now());
            logTiming(correlation, request.fileType(), start,
                    semaphoreAcquiredAt, tempReadyAt, processStartAt,
                    processDoneAt, pdfLocatedAt, Instant.now(),
                    bytes.length);
            return new OfficeConversionResult(bytes, pageCount, bytes.length,
                    elapsed, request.fileType());
        } catch (OfficeConversionTimeoutException
                 | OfficeConversionUnsupportedFormatException
                 | OfficeConversionInputTooLargeException
                 | OfficeConversionOutputTooLargeException
                 | OfficeConversionInvalidOutputException
                 | OfficeConversionStartupException
                 | OfficeConversionIoException
                 | OfficeConversionConfigurationException
                 | OfficeConversionInterruptedException
                 | OfficeConversionProcessException e) {
            throw e;
        } catch (IOException ioe) {
            // Local filesystem I/O during temp dir creation, input
            // write, output read or output inspection is RETRYABLE.
            throw new OfficeConversionIoException(
                    "Temporary Office conversion I/O failure", ioe);
        } finally {
            deleteBestEffort("input", inputDir, correlation);
            deleteBestEffort("output", outputDir, correlation);
            deleteBestEffort("profile", profileDir, correlation);
            if (acquired) {
                semaphore.release();
                acquired = false;
            }
        }
    }

    /**
     * Acquire the LibreOffice concurrency semaphore. This helper is the
     * SOLE point in the converter where an {@link InterruptedException}
     * may surface; it ALWAYS maps a real thread interruption to a
     * typed {@link OfficeConversionInterruptedException}, restores the
     * interrupt flag, and returns {@code true} only when the permit
     * was actually acquired. A {@code false} return value from
     * {@code tryAcquire} is mapped to
     * {@link OfficeConversionTimeoutException}.
     *
     * <p>The two outcomes are deliberately distinct:</p>
     * <ul>
     *   <li>{@code tryAcquire} returned {@code false}
     *       → {@link OfficeConversionTimeoutException},
     *       retryable, interrupt flag unchanged.</li>
     *   <li>{@code tryAcquire} threw {@link InterruptedException}
     *       → {@link OfficeConversionInterruptedException},
     *       retryable, interrupt flag restored before this method
     *       returns (by throwing).</li>
     * </ul>
     *
     * <p>No temporary directory is created and no process is started
     * when either outcome fires.</p>
     */
    private boolean acquirePermit() {
        try {
            boolean acquired = semaphore.tryAcquire(
                    properties.getSemaphoreWaitTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!acquired) {
                // Phase-4 timeout policy: the semaphore wait is
                // also a TIMEOUT that counts toward the per-artifact
                // retry budget. A second semaphore timeout for the
                // same artifact is treated as terminal.
                // The correlation id is not available here because
                // the converter is called before the request knows
                // the artifact id; we therefore surface the
                // existing typed timeout exception and let the
                // caller-side recordTimeout be a no-op. The
                // semaphores-acquire TIMEOUT and the
                // process-execution TIMEOUT are treated as the
                // same retryable failure in the classifier.
                throw new OfficeConversionTimeoutException(
                        "LibreOffice semaphore not acquired within "
                                + properties.getSemaphoreWaitTimeout().toMillis() + "ms");
            }
            return true;
        } catch (InterruptedException error) {
            // Restore the flag before the typed exception leaves this
            // method. The semaphore permit was never acquired, so the
            // outer finally does not release anything.
            Thread.currentThread().interrupt();
            throw new OfficeConversionInterruptedException(
                    "Office conversion interrupted while waiting for a conversion permit",
                    error);
        }
    }

    /**
     * Build the LibreOffice argument list. Exposed package-private for
     * the converter tests; never use the {@link ProcessBuilder} string
     * form.
     */
    static List<String> buildArgumentList(String executable, Path profileDir,
                                          Path outputDir, Path inputFile) {
        if (executable == null || executable.isBlank()) {
            throw new OfficeConversionConfigurationException(
                    "LibreOffice executable is not configured");
        }
        if (profileDir == null || outputDir == null || inputFile == null) {
            throw new OfficeConversionConfigurationException(
                    "Profile / output / input directories are not configured");
        }
        String userInstallation =
                "-env:UserInstallation=" + profileDir.toUri().toASCIIString();

        List<String> argv = new ArrayList<>();
        argv.add(executable);
        argv.add("--headless");
        argv.add("--invisible");
        argv.add("--nologo");
        argv.add("--nodefault");
        argv.add("--norestore");
        argv.add("--nolockcheck");
        argv.add("--nofirststartwizard");
        argv.add("-env:UNO_JAVA_JFW_INSTALL_DATA=");
        argv.add(userInstallation);
        argv.add("--convert-to");
        argv.add("pdf:writer_pdf_Export");
        argv.add("--outdir");
        argv.add(outputDir.toString());
        argv.add(inputFile.toString());
        return argv;
    }

    /**
     * Locate the unique PDF output in {@code outputDir}. Throws a
     * typed terminal exception when the directory is empty or holds
     * multiple candidate PDFs.
     */
    static Path locateSinglePdf(Path outputDir) throws IOException {
        if (outputDir == null || !Files.isDirectory(outputDir)) {
            throw new OfficeConversionInvalidOutputException("MISSING_OUTPUT",
                    "LibreOffice output directory is missing");
        }
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> walk = Files.list(outputDir)) {
            walk.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
                    candidates.add(p);
                }
            });
        }
        if (candidates.isEmpty()) {
            throw new OfficeConversionInvalidOutputException("NO_PDF",
                    "LibreOffice produced no PDF");
        }
        if (candidates.size() > 1) {
            throw new OfficeConversionInvalidOutputException("MULTIPLE_PDFS",
                    "LibreOffice produced "
                            + candidates.size() + " PDFs in the output directory");
        }
        return candidates.get(0);
    }

    private static String safeCorrelationId(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        // Bound and sanitise the correlation id so logs stay short
        // and never echo user-supplied secrets verbatim.
        String trimmed = raw.length() > 32 ? raw.substring(0, 32) : raw;
        return trimmed.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static void logSafeRunResult(String correlation,
                                         com.cmcu.itstudy.enums.AllowedDocumentFileType fileType,
                                         ProcessExecutionResult result) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info("soffice correlation={} type={} exit={} timedOut={} reason={} stderr={}",
                correlation,
                fileType == null ? "?" : fileType.name(),
                result.exitCode(),
                result.timedOut(),
                result.terminationReason(),
                safeTruncate(result.boundedStderrSummary(), 256));
    }

    /**
     * Structured per-step timing log. Emitted exactly once per
     * successful conversion so the operator can see how the wall-clock
     * budget is split across phases (semaphore wait, temp-dir setup,
     * process startup, conversion, output inspection). No key/token/
     * signed URL is ever included; only a short sanitised correlation
     * id, the file type enum, the page count and the resulting PDF
     * size in bytes.
     */
    private static void logTiming(String correlation,
                                  com.cmcu.itstudy.enums.AllowedDocumentFileType fileType,
                                  Instant start,
                                  Instant semaphoreAcquiredAt,
                                  Instant tempReadyAt,
                                  Instant processStartAt,
                                  Instant processDoneAt,
                                  Instant pdfLocatedAt,
                                  Instant finishedAt,
                                  long outputBytes) {
        if (!log.isInfoEnabled()) {
            return;
        }
        long semaphoreWaitMs = semaphoreAcquiredAt == null
                ? -1L
                : Duration.between(start, semaphoreAcquiredAt).toMillis();
        long tempSetupMs = tempReadyAt == null || semaphoreAcquiredAt == null
                ? -1L
                : Duration.between(semaphoreAcquiredAt, tempReadyAt).toMillis();
        long processMs = processStartAt == null || processDoneAt == null
                ? -1L
                : Duration.between(processStartAt, processDoneAt).toMillis();
        long pdfMs = pdfLocatedAt == null || processDoneAt == null
                ? -1L
                : Duration.between(processDoneAt, pdfLocatedAt).toMillis();
        long totalMs = Duration.between(start, finishedAt).toMillis();
        log.info("conversion-timing correlation={} type={} semaphoreWaitMs={} "
                        + "tempSetupMs={} processMs={} pdfMs={} totalMs={} outputBytes={}",
                correlation,
                fileType == null ? "?" : fileType.name(),
                semaphoreWaitMs, tempSetupMs, processMs, pdfMs,
                totalMs, outputBytes);
    }

    private static String safeTruncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static void deleteBestEffort(String label, Path dir, String correlation) {
        if (dir == null) {
            return;
        }
        try {
            if (!Files.exists(dir)) {
                return;
            }
            Set<Path> roots = new HashSet<>();
            roots.add(dir);
            try (var stream = Files.list(dir)) {
                stream.forEach(p -> roots.add(p));
            }
            for (Path p : roots) {
                try {
                    if (Files.isDirectory(p)) {
                        deleteRecursively(p);
                    } else {
                        Files.deleteIfExists(p);
                    }
                } catch (IOException inner) {
                    // Do not let cleanup failure mask primary exception.
                    warnCleanupFailure(label, correlation, inner);
                }
            }
            try {
                Files.deleteIfExists(dir);
            } catch (IOException inner) {
                warnCleanupFailure(label, correlation, inner);
            }
        } catch (IOException ioe) {
            warnCleanupFailure(label, correlation, ioe);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            // Delete leaves first, then root.
            List<Path> all = new ArrayList<>();
            stream.forEach(all::add);
            all.sort((a, b) -> b.toString().length() - a.toString().length());
            for (Path p : all) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p,
                            BasicFileAttributes.class);
                    if (attrs.isDirectory()) {
                        Files.deleteIfExists(p);
                    } else {
                        Files.deleteIfExists(p);
                    }
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private static void warnCleanupFailure(String label, String correlation,
                                           Throwable cause) {
        if (!log.isWarnEnabled()) {
            return;
        }
        log.warn("cleanup-failed label={} correlation={} cause={}",
                label, correlation, cause.getClass().getSimpleName());
    }

    /**
     * Visible for tests: confirm the converter owns exactly one
     * semaphore reference and that the reference matches the
     * configured permit count.
     */
    int configuredPermits() {
        return Math.max(1, properties.getMaxConcurrentConversions());
    }

    static Path resolveOnTempRoot(String sub) {
        return Paths.get(System.getProperty("java.io.tmpdir"), sub);
    }
}
