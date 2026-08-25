package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.autoquiz.AutoQuizCallbackRequestDto;
import com.cmcu.itstudy.dto.autoquiz.AutoQuizCallbackResponseDto;
import com.cmcu.itstudy.dto.autoquiz.AutoQuizTechnicalFailureRequestDto;
import com.cmcu.itstudy.handle.AutoQuizCallbackAccessDeniedException;
import com.cmcu.itstudy.handle.AutoQuizSourceAccessDeniedException;
import com.cmcu.itstudy.service.contract.AutoQuizCallbackService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phase 2E REST controller for the n8n-to-backend success callback.
 *
 * <h2>Endpoint</h2>
 * <pre>POST /api/auto-quiz/generations/{generationId}/complete</pre>
 *
 * <h2>Authentication</h2>
 * <p>The endpoint is unauthenticated at the Spring Security level
 * (it is listed in {@code SecurityConfig} as {@code permitAll()}). The
 * n8n machine-to-machine authentication is performed by the service
 * layer: the {@code X-Auto-Quiz-Dispatch-Token} header value must
 * exactly match the {@code dispatchToken} stored on the
 * {@code QuizGeneration} row for the given {@code generationId}.
 *
 * <h2>Request headers</h2>
 * <ul>
 *   <li>{@code X-Auto-Quiz-Dispatch-Token} (required) &mdash; the
 *       dispatch token issued when the generation was claimed by
 *       the dispatcher.</li>
 *   <li>{@code Content-Type: application/json}</li>
 * </ul>
 *
 * <h2>Response contract</h2>
 * <ul>
 *   <li>HTTP 200 &mdash; callback accepted:
 *       {@code {accepted: true, status: "READY", generationId, quizId}}</li>
 *   <li>HTTP 403 &mdash; rejected (wrong/missing token, CANCELLED):
 *       {@code {accepted: false, status, generationId, message}}</li>
 *   <li>HTTP 400 &mdash; malformed request body:
 *       {@code {success: false, message: "..."}}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auto-quiz")
public class AutoQuizCallbackController {

    private static final Logger log =
            LoggerFactory.getLogger(AutoQuizCallbackController.class);

    public static final String HEADER_DISPATCH_TOKEN =
            "X-Auto-Quiz-Dispatch-Token";

    private final AutoQuizCallbackService callbackService;

    public AutoQuizCallbackController(AutoQuizCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    /**
     * n8n success callback. Persists the generated quiz and transitions
     * the generation to READY.
     */
    @PostMapping(
            value = "/generations/{generationId}/complete",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AutoQuizCallbackResponseDto> completeGeneration(
            @PathVariable("generationId") UUID generationId,
            @RequestHeader(value = HEADER_DISPATCH_TOKEN, required = false)
                    String dispatchTokenRaw,
            @Valid @RequestBody AutoQuizCallbackRequestDto request) {

        UUID suppliedToken = parseDispatchToken(dispatchTokenRaw);

        log.info(
                "Auto Quiz callback received: generationId={}",
                generationId);

        AutoQuizCallbackResponseDto response =
                callbackService.processCallback(
                        generationId, suppliedToken, request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Phase 5A — n8n business-rejection callback.
     *
     * <p>Used by the dispatch worker to report that a generation must
     * NOT be turned into a {@code Quiz} row because a semantic /
     * business condition failed (initially: focus-topic mismatch with
     * the document content). The backend hard-codes the rejection
     * code; clients MUST NOT be able to supply an arbitrary
     * {@code lastError} string.</p>
     *
     * <h3>Authentication</h3>
     * <p>Identical to {@code /complete}: the endpoint is permit-all at
     * Spring Security level; the per-row {@code dispatchToken} guards
     * access via the service layer.</p>
     *
     * <h3>Request</h3>
     * <ul>
     *   <li>Path: {@code POST /api/auto-quiz/generations/{generationId}/reject}</li>
     *   <li>Header: {@code X-Auto-Quiz-Dispatch-Token: <uuid>}</li>
     *   <li>Body: none.</li>
     * </ul>
     *
     * <h3>Response</h3>
     * <ul>
     *   <li>HTTP 200 &mdash; rejection accepted:
     *       {@code {accepted: true, status: "FAILED", generationId, message: "Generation rejected"}}</li>
     *   <li>HTTP 403 &mdash; rejected (wrong/missing token, not in
     *       PROCESSING state, lease invalidated by CANCELLED race):
     *       {@code {accepted: false, status, generationId, message}}</li>
     * </ul>
     *
     * <h3>Side effects</h3>
     * <p>On HTTP 200 the generation row transitions
     * {@code PROCESSING -> FAILED} with {@code lastError =
     * "FOCUS_TOPIC_MISMATCH"}. No {@code Quiz}, no questions, no
     * options, no {@code DocumentQuiz} association is created.</p>
     */
    @PostMapping(
            value = "/generations/{generationId}/reject",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AutoQuizCallbackResponseDto> rejectGeneration(
            @PathVariable("generationId") UUID generationId,
            @RequestHeader(value = HEADER_DISPATCH_TOKEN, required = false)
                    String dispatchTokenRaw) {

        UUID suppliedToken = parseDispatchToken(dispatchTokenRaw);

        log.info(
                "Auto Quiz business-rejection callback received: "
                        + "generationId={}",
                generationId);

        AutoQuizCallbackResponseDto response =
                callbackService.processBusinessRejection(
                        generationId, suppliedToken);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Phase 7B.3 — n8n technical-failure callback.
     *
     * <p>Used by the dispatch worker to report a TECHNICAL failure
     * (Structured Output Parser failed, JSON schema invalid,
     * upstream node crashed before any AI output, etc.). This is
     * distinct from the {@code /reject} business-rejection path:
     * the {@code /reject} endpoint hard-codes
     * {@code FOCUS_TOPIC_MISMATCH} which would lie about the
     * document-content match if it were used to report a parser
     * failure.</p>
     *
     * <h3>Authentication</h3>
     * <p>Identical to {@code /complete} and {@code /reject}: the
     * endpoint is permit-all at Spring Security level; the
     * per-row {@code dispatchToken} guards access via the service
     * layer.</p>
     *
     * <h3>Request</h3>
     * <ul>
     *   <li>Path: {@code POST /api/auto-quiz/generations/{generationId}/fail}</li>
     *   <li>Header: {@code X-Auto-Quiz-Dispatch-Token: <uuid>}</li>
     *   <li>Body: {@link AutoQuizTechnicalFailureRequestDto}
     *       <ul>
     *         <li>{@code errorCode} (required, server-whitelisted:
     *             {@code AI_OUTPUT_INVALID},
     *             {@code AI_SCHEMA_INVALID} or
     *             {@code AI_WORKFLOW_FAILED})</li>
     *         <li>{@code message} (optional, bounded to 200 chars;
     *             sanitised server-side; logged but never echoed
     *             back to client; never persisted as
     *             {@code lastError})</li>
     *       </ul>
     *   </li>
     * </ul>
     *
     * <h3>Response</h3>
     * <ul>
     *   <li>HTTP 200 &mdash; technical failure accepted:
     *       {@code {accepted: true, status: "FAILED", generationId, message: "Generation failed"}}</li>
     *   <li>HTTP 403 &mdash; rejected (wrong/missing token, not in
     *       PROCESSING state, lease invalidated by CANCELLED race):
     *       {@code {accepted: false, status, generationId, message}}</li>
     *   <li>HTTP 400 &mdash; malformed body, missing errorCode, or
     *       non-whitelisted errorCode:
     *       {@code {success: false, message: "..."}}</li>
     * </ul>
     *
     * <h3>Side effects</h3>
     * <p>On HTTP 200 the generation row transitions
     * {@code PROCESSING -> FAILED} with {@code lastError} set to
     * the whitelisted {@code errorCode}. No {@code Quiz}, no
     * questions, no options, no {@code DocumentQuiz} association
     * is created. The row remains in history; the FE retry button
     * creates a brand-new {@code QuizGeneration} row.</p>
     */
    @PostMapping(
            value = "/generations/{generationId}/fail",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AutoQuizCallbackResponseDto> failGeneration(
            @PathVariable("generationId") UUID generationId,
            @RequestHeader(value = HEADER_DISPATCH_TOKEN, required = false)
                    String dispatchTokenRaw,
            @Valid @RequestBody AutoQuizTechnicalFailureRequestDto request) {

        UUID suppliedToken = parseDispatchToken(dispatchTokenRaw);

        log.info(
                "Auto Quiz technical-failure callback received: "
                        + "generationId={}",
                generationId);

        AutoQuizCallbackResponseDto response =
                callbackService.processTechnicalFailure(
                        generationId, suppliedToken, request);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    private static UUID parseDispatchToken(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
