package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.scheduler.DocumentPreviewWorker;
import com.cmcu.itstudy.service.contract.DocumentPreviewArtifactReadySignal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of
 * {@link DocumentPreviewArtifactReadySignal}.
 *
 * <p>This bean is intentionally a singleton Spring component so the
 * publisher (the artifact state service) and the subscriber (the
 * worker) share the same instance. Multi-instance deployments
 * benefit from the local listener fan-out: a FULL READY transition
 * that happens on instance&nbsp;A wakes instance&nbsp;A's worker;
 * instance&nbsp;B's worker still wakes up via its own
 * {@code fixed-delay-ms} tick, which is acceptable because the
 * claim SQL is the cross-instance atomic owner.</p>
 */
@Component
public class DocumentPreviewArtifactReadySignalImpl
        implements DocumentPreviewArtifactReadySignal {

    private final List<DocumentPreviewWorker> listeners =
            new CopyOnWriteArrayList<>();

    @Override
    public void fire() {
        for (DocumentPreviewWorker worker : listeners) {
            try {
                worker.wakeUp();
            } catch (RuntimeException e) {
                // The wake-up is best-effort; a listener failure must
                // never break the publisher's markReady transaction.
            }
        }
    }

    @Override
    public void attach(DocumentPreviewWorker worker) {
        if (worker != null) {
            listeners.add(worker);
        }
    }

    @Override
    public void detach(DocumentPreviewWorker worker) {
        if (worker != null) {
            listeners.remove(worker);
        }
    }
}
