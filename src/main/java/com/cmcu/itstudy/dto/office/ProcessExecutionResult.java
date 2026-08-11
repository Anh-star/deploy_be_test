package com.cmcu.itstudy.dto.office;

import java.time.Duration;

/**
 * Result returned by {@code SafeProcessRunner.run(...)}.
 *
 * <p>The runner never returns the underlying {@link Process} reference
 * to its callers. All callers see is this result record, so the
 * runner retains exclusive ownership of the process tree and the
 * stream executor.</p>
 *
 * @param exitCode             nullable exit code; {@code null} when the
 *                             process exit code could not be observed
 *                             (forced termination timeout, process
 *                             disappeared, startup failure)
 * @param timedOut             true when the runner hit the configured
 *                             {@link ProcessRunOptions#timeout}
 * @param boundedStdoutSummary captured prefix of stdout (UTF-8)
 * @param boundedStderrSummary captured prefix of stderr (UTF-8)
 * @param duration             wall-clock duration of the run
 * @param terminationReason    why the run ended
 */
public record ProcessExecutionResult(
        Integer exitCode,
        boolean timedOut,
        String boundedStdoutSummary,
        String boundedStderrSummary,
        Duration duration,
        ProcessTerminationReason terminationReason) {
}
