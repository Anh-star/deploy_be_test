package com.cmcu.itstudy.dto.office;

/**
 * Termination reason reported by {@code SafeProcessRunner}.
 *
 * <p>The runner never invents an exit code when the process could not
 * be observed cleanly; instead it surfaces an explicit
 * {@link ProcessTerminationReason} so callers can distinguish a clean
 * exit from a timeout, a forced kill, an interrupt, or a startup
 * failure.</p>
 */
public enum ProcessTerminationReason {
    /** Process exited normally within the configured budget. */
    NORMAL_EXIT,
    /** Process exceeded the configured conversion timeout. */
    TIMEOUT,
    /** Process did not honour graceful termination; escalated to forced kill. */
    FORCED_TERMINATION,
    /** Thread was interrupted while waiting for the process. */
    INTERRUPTED,
    /** Process could not be started at all (e.g. executable missing). */
    STARTUP_FAILURE
}
