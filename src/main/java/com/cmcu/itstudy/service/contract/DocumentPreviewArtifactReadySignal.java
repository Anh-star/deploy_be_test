package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.scheduler.DocumentPreviewWorker;

/**
 * Application-level signal that fires whenever a FULL preview
 * artifact transitions to {@code READY}.
 *
 * <p>The signal is the wake-up trigger for the latency-optimised
 * worker: when a FULL becomes READY, the dependent LIMITED row for
 * the same source is now claimable, so the worker should run its
 * next cycle immediately rather than waiting for the
 * {@code fixed-delay-ms} tick.</p>
 *
 * <p>The signal uses a thread-safe
 * {@link java.util.concurrent.CopyOnWriteArrayList} of listeners so
 * one wake-up notifies every attached worker regardless of which JVM
 * instance emitted it. This is required because the
 * {@code DocumentPreviewArtifactStateService} that publishes the
 * signal runs in a different transaction from the worker that
 * consumes it.</p>
 */
public interface DocumentPreviewArtifactReadySignal {

    /**
     * Fires the wake-up signal. Called by the artifact state service
     * after a successful {@code markReady(FULL)} transition.
     */
    void fire();

    /**
     * Subscribes a worker instance to the signal.
     */
    void attach(DocumentPreviewWorker worker);

    /**
     * Unsubscribes a worker instance.
     */
    void detach(DocumentPreviewWorker worker);
}
