package com.cmcu.itstudy.enums;

/**
 * Lifecycle status of a {@code DocumentPreviewArtifact}.
 *
 * <p>Phase&nbsp;O2 only defines the database-visible lifecycle states.
 * Transition rules are enforced by guarded UPDATE statements in the
 * claim repository (see
 * {@code DocumentPreviewArtifactClaimRepository}); the worker (added in
 * Phase&nbsp;O3) is responsible for triggering them, not this
 * enumeration.</p>
 *
 * <p>State diagram:</p>
 * <pre>
 *   PENDING ──claim──▶ PROCESSING ──success──▶ READY
 *      │                  │
 *      │                  ├──retryable failure──▶ RETRY ──claim──▶ PROCESSING
 *      │                  │
 *      │                  └──terminal failure────▶ DEAD
 *      │
 *      └──retryable failure──▶ RETRY ──claim──▶ PROCESSING
 * </pre>
 *
 * <p>The status {@code WAITING_CLEANUP} that earlier draft contracts
 * referenced is intentionally NOT a separate value. A row that is
 * blocked by an in-flight storage cleanup task is represented by the
 * combination {@code status = RETRY} + {@code lastError =
 * "WAITING_CLEANUP"} + {@code cleanupTaskId != null}.</p>
 */
public enum DocumentPreviewArtifactStatus {
    PENDING,
    PROCESSING,
    READY,
    RETRY,
    DEAD
}
