package com.cmcu.itstudy.handle;

/**
 * Phase&nbsp;O4B: artifact-state race raised by
 * {@code DocumentPreviewArtifactDeliveryService} when the artifact row
 * changed status between the controller's READY check and the
 * delivery service's read-time re-validation.
 *
 * <p>The controller translates this into the actual current waiting /
 * dead state JSON after re-querying the preview state service. The
 * exception is NOT a terminal delivery error: it is a transient
 * race marker.</p>
 */
public class PreviewArtifactStateRaceException extends RuntimeException {
    public PreviewArtifactStateRaceException(String message) {
        super(message);
    }
    public PreviewArtifactStateRaceException(String message, Throwable cause) {
        super(message, cause);
    }
}