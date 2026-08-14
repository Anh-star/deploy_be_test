package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.autoquiz.AutoQuizCallbackRequestDto;
import com.cmcu.itstudy.dto.autoquiz.AutoQuizCallbackResponseDto;
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
