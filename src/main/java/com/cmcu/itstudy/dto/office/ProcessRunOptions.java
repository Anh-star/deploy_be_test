package com.cmcu.itstudy.dto.office;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options for a single {@code SafeProcessRunner.run(...)} call.
 *
 * <p>The runner inherits the JVM environment by default. The optional
 * {@link #envOverrides} map is layered on top of the inherited
 * environment. The runner never clears {@code PATH} and never logs
 * the resulting environment.</p>
 *
 * <p>This record is intentionally immutable. Each
 * {@code SafeProcessRunner.run(...)} call computes its own internal
 * deadline timestamps from the supplied {@link Duration}s.</p>
 *
 * @param timeout                          wall-clock budget for the process
 * @param gracePeriod                      grace period between {@code destroy()}
 *                                         and {@code destroyForcibly()}
 * @param forcedTerminationTimeout         maximum wait after forced termination
 * @param streamDrainTimeout               bounded drain wait for stream consumers
 * @param stdoutCaptureMaxBytes            maximum bytes captured from stdout
 * @param stderrCaptureMaxBytes            maximum bytes captured from stderr
 * @param envOverrides                     optional environment overrides (null = none)
 */
public record ProcessRunOptions(
        Duration timeout,
        Duration gracePeriod,
        Duration forcedTerminationTimeout,
        Duration streamDrainTimeout,
        int stdoutCaptureMaxBytes,
        int stderrCaptureMaxBytes,
        Map<String, String> envOverrides) {

    public ProcessRunOptions {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (gracePeriod == null || gracePeriod.isNegative()) {
            throw new IllegalArgumentException("gracePeriod must be non-negative");
        }
        if (forcedTerminationTimeout == null || forcedTerminationTimeout.isNegative()) {
            throw new IllegalArgumentException("forcedTerminationTimeout must be non-negative");
        }
        if (streamDrainTimeout == null || streamDrainTimeout.isNegative()) {
            throw new IllegalArgumentException("streamDrainTimeout must be non-negative");
        }
        if (stdoutCaptureMaxBytes <= 0) {
            throw new IllegalArgumentException("stdoutCaptureMaxBytes must be positive");
        }
        if (stderrCaptureMaxBytes <= 0) {
            throw new IllegalArgumentException("stderrCaptureMaxBytes must be positive");
        }
        envOverrides = envOverrides == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(envOverrides));
    }
}
