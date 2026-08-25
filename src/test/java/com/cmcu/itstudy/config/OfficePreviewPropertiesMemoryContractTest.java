package com.cmcu.itstudy.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7B — pins the tightened memory-safe contract of
 * {@link OfficePreviewProperties} introduced to keep a single DOC/DOCX
 * FULL conversion inside the Render Free 512 MB cgroup.
 *
 * <p>Three invariants are observable without spinning up the Spring
 * context, a real LibreOffice process or any I/O:</p>
 * <ol>
 *   <li>The {@code maxConcurrentConversions} default is exactly
 *       {@code 1}. The previous default of {@code 2} allowed two
 *       LibreOffice child processes to run in parallel; each can hold
 *       100–200 MB of native memory on a complex DOCX, which on the
 *       512 MB cgroup has been observed to OOM-kill the JVM.</li>
 *   <li>The {@code maxConcurrentConversions} validator rejects
 *       {@code 0} and negative values; a misconfiguration must fail
 *       fast at Spring bean creation time.</li>
 *   <li>Existing defaults (timeouts, byte caps, semaphore wait timeout,
 *       diagnostic capture cap) remain untouched.</li>
 * </ol>
 */
class OfficePreviewPropertiesMemoryContractTest {

    @Test
    @DisplayName("default maxConcurrentConversions is exactly 1 (Phase 7B)")
    void defaultMaxConcurrentConversionsIsOne() {
        OfficePreviewProperties properties = new OfficePreviewProperties();
        assertEquals(1, properties.getMaxConcurrentConversions(),
                "Phase 7B default must be 1 to keep a DOC/DOCX FULL "
                        + "conversion inside the Render Free 512 MB cgroup");
    }

    @Test
    @DisplayName("maxConcurrentConversions=0 fails fast at validate()")
    void zeroPermitsRejected() {
        OfficePreviewProperties properties = new OfficePreviewProperties();
        properties.setMaxConcurrentConversions(0);
        IllegalStateException error = null;
        try {
            properties.validate();
        } catch (IllegalStateException e) {
            error = e;
        }
        assertTrue(error != null,
                "validate() must reject maxConcurrentConversions < 1");
        assertTrue(error.getMessage().contains("maxConcurrentConversions"),
                "error message must name the offending field");
    }

    @Test
    @DisplayName("negative maxConcurrentConversions fails fast at validate()")
    void negativePermitsRejected() {
        OfficePreviewProperties properties = new OfficePreviewProperties();
        properties.setMaxConcurrentConversions(-1);
        IllegalStateException error = null;
        try {
            properties.validate();
        } catch (IllegalStateException e) {
            error = e;
        }
        assertTrue(error != null,
                "validate() must reject negative maxConcurrentConversions");
    }

    @Test
    @DisplayName("existing byte-cap and timeout defaults are unchanged")
    void existingDefaultsUnchanged() {
        OfficePreviewProperties properties = new OfficePreviewProperties();
        assertEquals(Duration.ofSeconds(90), properties.getConversionTimeout());
        assertEquals(Duration.ofSeconds(2),
                properties.getProcessTerminationGracePeriod());
        assertEquals(Duration.ofSeconds(5),
                properties.getProcessForcedTerminationTimeout());
        assertEquals(Duration.ofSeconds(2), properties.getStreamDrainTimeout());
        assertEquals(Duration.ofSeconds(10),
                properties.getSemaphoreWaitTimeout());
        assertEquals(25L * 1024L * 1024L, properties.getMaxInputBytes());
        assertEquals(25L * 1024L * 1024L, properties.getMaxOutputBytes());
        assertEquals(1024, properties.getDiagnosticCaptureBytes());
    }
}
