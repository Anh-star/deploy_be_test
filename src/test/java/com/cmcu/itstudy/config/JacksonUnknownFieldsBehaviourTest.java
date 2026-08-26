package com.cmcu.itstudy.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7B.2 — pins the EFFECTIVE Jackson configuration that the
 * n8n-to-backend callback endpoints run against.
 *
 * <p>The application defines an explicit
 * {@code @Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }}
 * in {@link JacksonConfig}. That {@code new ObjectMapper()} call
 * DOES <strong>NOT</strong> inherit Spring Boot's default mapper
 * customisations (which would have disabled
 * {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}).
 * The constructor returns a fresh Jackson mapper with ALL the
 * library defaults, including the strict unknown-field rejection
 * behaviour.</p>
 *
 * <p>This test observes the live mapper the bean would produce
 * at runtime. The current observation is that the mapper REJECTS
 * unknown fields, which is in tension with the Phase&nbsp;7B.1
 * recommendation that n8n could safely send extra fields such as
 * {@code focusMatched} / {@code focusReason} on the
 * {@code /complete} callback.</p>
 *
 * <p>The contract n8n should adopt is now strict: ONLY the
 * fields declared on
 * {@code AutoQuizCallbackRequestDto} may be sent. Any extra
 * field will trigger an HTTP 400.</p>
 */
class JacksonUnknownFieldsBehaviourTest {

    @Test
    @DisplayName("effective FAIL_ON_UNKNOWN_PROPERTIES on JacksonConfig's mapper")
    void effectiveFeatureFlagOnConfiguredBean() {
        ObjectMapper configured = new JacksonConfig().objectMapper();
        boolean enabled = configured.getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // Record what the bean currently does. The 7B.1 report
        // assumed this would be false; the live configuration
        // proves otherwise. We assert the value matches what
        // JacksonConfig actually emits today.
        assertTrue(
                configured.getDeserializationConfig().isEnabled(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                "Configured ObjectMapper rejects unknown fields "
                        + "(Jackson default on this codebase). n8n "
                        + "MUST NOT send any field that is not declared "
                        + "on AutoQuizCallbackRequestDto.");
    }

    @Test
    @DisplayName("library default for FAIL_ON_UNKNOWN_PROPERTIES is enabled")
    void libraryDefault() {
        // Document the upstream default so future readers know
        // what the configured bean inherits.
        ObjectMapper bare = new ObjectMapper();
        assertTrue(
                bare.getDeserializationConfig().isEnabled(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
                "Jackson library default rejects unknown fields. "
                        + "The configured JacksonConfig bean returns "
                        + "the same default — Spring Boot's relaxing "
                        + "customiser does NOT attach to a hand-rolled "
                        + "@Bean ObjectMapper unless the bean factory "
                        + "is given the Spring Boot ObjectMapper "
                        + "customisers explicitly.");
    }

    @Test
    @DisplayName("configured mapper THROWS on unknown field for HashMap target")
    void throwsOnUnknownField() {
        ObjectMapper configured = new JacksonConfig().objectMapper();
        String json = "{\"a\":1,\"unexpected\":2,\"b\":3}";
        // The configured mapper rejects unknown fields when the
        // target type is a strongly-typed bean. For HashMap
        // targets Jackson never throws because every property is
        // legal. To prove the strict behaviour we exercise a
        // strongly-typed target via a one-off POJO.
        assertThrows(com.fasterxml.jackson.databind.exc
                        .UnrecognizedPropertyException.class,
                () -> configured.readValue(json, Probe.class),
                "Configured mapper must reject unknown fields on a "
                        + "strongly-typed target.");
    }

    @Test
    @DisplayName("configured mapper ACCEPTs known fields only on AutoQuizCallbackRequestDto")
    void probeAcceptsKnownField() {
        ObjectMapper configured = new JacksonConfig().objectMapper();
        String json = "{\"known\":\"value\"}";
        assertDoesNotThrow(() -> {
            Probe p = configured.readValue(json, Probe.class);
            assertTrue("value".equals(p.getKnown()),
                    "known field is bound");
        });
    }

    @Test
    @DisplayName("configured mapper serializes LocalDateTime as UTC ISO-8601 string with Z suffix")
    void serializesLocalDateTimeWithZ() throws Exception {
        ObjectMapper configured = new JacksonConfig().objectMapper();
        java.time.LocalDateTime dt = java.time.LocalDateTime.of(2026, 8, 26, 21, 0, 0);
        String json = configured.writeValueAsString(dt);
        assertTrue(json.contains("2026-08-26T21:00:00") && json.endsWith("Z\""),
                "Serialized LocalDateTime must end with Z to be correctly recognized as UTC by browser: " + json);
    }

    @Test
    @DisplayName("configured mapper deserializes LocalDateTime from strings with or without Z")
    void deserializesLocalDateTime() throws Exception {
        ObjectMapper configured = new JacksonConfig().objectMapper();
        java.time.LocalDateTime dtWithZ = configured.readValue("\"2026-08-26T21:00:00Z\"", java.time.LocalDateTime.class);
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDateTime.of(2026, 8, 26, 21, 0, 0), dtWithZ);

        java.time.LocalDateTime dtWithoutZ = configured.readValue("\"2026-08-26T21:00:00\"", java.time.LocalDateTime.class);
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDateTime.of(2026, 8, 26, 21, 0, 0), dtWithoutZ);
    }

    /**
     * Minimal strongly-typed probe POJO with ONE declared field.
     * Used to prove the deserialiser's unknown-field policy.
     */
    public static final class Probe {
        private String known;
        public String getKnown() { return known; }
        public void setKnown(String known) { this.known = known; }
    }
}