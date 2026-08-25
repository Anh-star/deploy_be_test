package com.cmcu.itstudy.dto.autoquiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phase 7B.3 — request body for the technical-failure callback.
 *
 * <p>Sent by the n8n workflow when it cannot deliver a parsable,
 * schema-valid, AI-generated quiz. This is the <strong>technical</strong>
 * failure path; the business / semantic rejection (focus-topic
 * mismatch) is reported through {@code POST /reject} with no body
 * and a hard-coded {@code FOCUS_TOPIC_MISMATCH} server-side code.</p>
 *
 * <h3>Validation summary</h3>
 * <ul>
 *   <li>{@code errorCode} &mdash; required, must match
 *       {@link #ALLOWED_ERROR_CODE_PATTERN}: uppercase letters,
 *       digits and {@code _} only. The whitelist of values n8n is
 *       actually allowed to send is enforced by the service layer
 *       (see {@code AutoQuizCallbackServiceImpl.processTechnicalFailure})
 *       so any unknown string is rejected with HTTP 400.</li>
 *   <li>{@code message} &mdash; optional, bounded to 200 characters.
 *       The service layer sanitises this further through
 *       {@code SafeArtifactLastError} so raw stack traces or model
 *       output NEVER lands in the database.</li>
 * </ul>
 *
 * <p>The service layer is the source of truth for what gets stored
 * on {@code lastError} &mdash; the supplied {@code errorCode} is
 * validated against the whitelist below and the persisted value
 * is the sanitised form of that whitelist entry. The supplied
 * {@code message} is used only for server-side logs and is never
 * echoed back to the client.</p>
 *
 * <h3>Allowed error codes</h3>
 * <ul>
 *   <li>{@link #CODE_AI_OUTPUT_INVALID} &mdash; the AI returned text
 *       that could not be parsed into structured JSON.</li>
 *   <li>{@link #CODE_AI_SCHEMA_INVALID} &mdash; the AI returned JSON
 *       that did not match the expected schema (missing fields,
 *       wrong types, wrong question count, etc.).</li>
 *   <li>{@link #CODE_AI_WORKFLOW_FAILED} &mdash; an upstream node
 *       (HTTP, Code, Tool) inside the workflow raised an error
 *       before any AI output was produced.</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoQuizTechnicalFailureRequestDto {

    /**
     * Whitelist of error codes the backend accepts on this
     * callback. Any value outside this set is rejected with
     * HTTP 400. The hard-coding here mirrors the
     * {@code FOCUS_TOPIC_MISMATCH} whitelist on the
     * business-rejection callback.
     */
    public static final String CODE_AI_OUTPUT_INVALID = "AI_OUTPUT_INVALID";
    public static final String CODE_AI_SCHEMA_INVALID = "AI_SCHEMA_INVALID";
    public static final String CODE_AI_WORKFLOW_FAILED = "AI_WORKFLOW_FAILED";

    /**
     * Tight character class for {@link #errorCode}: uppercase
     *       ASCII letters, digits and {@code _} only. The pattern
     *       is intentionally narrow so any HTML, JSON, control
     *       character, whitespace or unicode token in the value
     *       is rejected before the service layer sees it.
     */
    public static final String ALLOWED_ERROR_CODE_PATTERN = "[A-Z0-9_]+";

    @NotBlank(message = "errorCode is required")
    @Size(min = 1, max = 40,
            message = "errorCode must be 1..40 characters")
    @Pattern(regexp = ALLOWED_ERROR_CODE_PATTERN,
            message = "errorCode must contain only uppercase "
                    + "letters, digits and underscores")
    private String errorCode;

    @Size(max = 200, message = "message must not exceed 200 characters")
    private String message;
}