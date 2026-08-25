package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.autoquiz.AutoQuizTechnicalFailureRequestDto;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import com.cmcu.itstudy.handle.AutoQuizCallbackAccessDeniedException;
import com.cmcu.itstudy.handle.AutoQuizCallbackAccessDeniedException.Reason;
import com.cmcu.itstudy.repository.DocumentQuizRepository;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.repository.QuizQuestionOptionRepository;
import com.cmcu.itstudy.repository.QuizQuestionRepository;
import com.cmcu.itstudy.repository.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7B.3 — targeted contract tests for
 * {@link AutoQuizCallbackServiceImpl#processTechnicalFailure}.
 *
 * <p>The technical-failure callback is the third arm of the n8n
 * machine-to-machine callback surface (after {@code /complete} and
 * {@code /reject}). It is intentionally narrow: it accepts ONLY a
 * whitelisted {@code errorCode} value, transitions the row
 * {@code PROCESSING -> FAILED} with no Quiz / questions / options
 * side effects, and never echoes the supplied message back to
 * the client.</p>
 */
class AutoQuizCallbackTechnicalFailureTest {

    private QuizGenerationRepository generationRepository;
    private QuizRepository quizRepository;
    private QuizQuestionRepository questionRepository;
    private QuizQuestionOptionRepository optionRepository;
    private DocumentQuizRepository documentQuizRepository;
    private AutoQuizCallbackServiceImpl service;

    @BeforeEach
    void setUp() {
        generationRepository = mock(QuizGenerationRepository.class);
        quizRepository = mock(QuizRepository.class);
        questionRepository = mock(QuizQuestionRepository.class);
        optionRepository = mock(QuizQuestionOptionRepository.class);
        documentQuizRepository = mock(DocumentQuizRepository.class);
        service = new AutoQuizCallbackServiceImpl(
                generationRepository,
                quizRepository,
                questionRepository,
                optionRepository,
                documentQuizRepository);
    }

    private QuizGeneration row(UUID id, UUID token, QuizGenerationStatus status) {
        QuizGeneration qg = QuizGeneration.builder()
                .id(id)
                .dispatchToken(token)
                .status(status)
                .build();
        return qg;
    }

    @Test
    @DisplayName("valid whitelisted errorCode: PROCESSING -> FAILED, no Quiz created")
    void validCodeTransitionsToFailed() {
        UUID genId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        AutoQuizTechnicalFailureRequestDto req =
                AutoQuizTechnicalFailureRequestDto.builder()
                        .errorCode(
                                AutoQuizTechnicalFailureRequestDto
                                        .CODE_AI_SCHEMA_INVALID)
                        .message("Parser returned malformed JSON")
                        .build();

        when(generationRepository.findById(genId))
                .thenReturn(Optional.of(row(genId, token,
                        QuizGenerationStatus.PROCESSING)));
        when(generationRepository.markFailedFromProcessing(
                eq(genId), eq(token), any(String.class),
                any(java.time.LocalDateTime.class)))
                .thenReturn(1);

        var response = service.processTechnicalFailure(
                genId, token, req);

        assertEquals(true, response.isAccepted());
        assertEquals("FAILED", response.getStatus());
        assertEquals(genId, response.getGenerationId());
        assertEquals("Generation failed", response.getMessage(),
                "Response message must be the safe constant; never "
                        + "echoes the supplied message or errorCode");
        verify(generationRepository).markFailedFromProcessing(
                eq(genId), eq(token), eq(
                        AutoQuizTechnicalFailureRequestDto
                                .CODE_AI_SCHEMA_INVALID),
                any(java.time.LocalDateTime.class));
        // No Quiz / question / option persistence side effects.
        verify(quizRepository, never()).save(any());
        verify(questionRepository, never()).save(any());
        verify(optionRepository, never()).saveAll(any());
        verify(documentQuizRepository, never()).save(any());
    }

    @Test
    @DisplayName("wrong token: rejected, no state change, no Quiz side effects")
    void wrongTokenRejected() {
        UUID genId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID wrongToken = UUID.randomUUID();
        AutoQuizTechnicalFailureRequestDto req =
                AutoQuizTechnicalFailureRequestDto.builder()
                        .errorCode(
                                AutoQuizTechnicalFailureRequestDto
                                        .CODE_AI_OUTPUT_INVALID)
                        .build();

        when(generationRepository.findById(genId))
                .thenReturn(Optional.of(row(genId, token,
                        QuizGenerationStatus.PROCESSING)));

        AutoQuizCallbackAccessDeniedException ex = assertThrows(
                AutoQuizCallbackAccessDeniedException.class,
                () -> service.processTechnicalFailure(
                        genId, wrongToken, req));
        assertEquals(Reason.TOKEN_MISMATCH, ex.reason());
        verify(generationRepository, never())
                .markFailedFromProcessing(any(), any(), any(), any());
        verify(quizRepository, never()).save(any());
    }

    @Test
    @DisplayName("non-PROCESSING generation: rejected with GENERATION_NOT_FOUND")
    void nonProcessingRejected() {
        UUID genId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        AutoQuizTechnicalFailureRequestDto req =
                AutoQuizTechnicalFailureRequestDto.builder()
                        .errorCode(
                                AutoQuizTechnicalFailureRequestDto
                                        .CODE_AI_WORKFLOW_FAILED)
                        .build();

        // The state can be QUEUED, READY, FAILED, CANCELLED, WAITING_SOURCE.
        // Use READY as the most-likely non-PROCESSING state at runtime.
        when(generationRepository.findById(genId))
                .thenReturn(Optional.of(row(genId, token,
                        QuizGenerationStatus.READY)));

        AutoQuizCallbackAccessDeniedException ex = assertThrows(
                AutoQuizCallbackAccessDeniedException.class,
                () -> service.processTechnicalFailure(
                        genId, token, req));
        assertEquals(Reason.GENERATION_NOT_FOUND, ex.reason());
        verify(generationRepository, never())
                .markFailedFromProcessing(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CANCELLED row: rejected, no resurrection (CANCELLED wins)")
    void cancelledWins() {
        UUID genId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        AutoQuizTechnicalFailureRequestDto req =
                AutoQuizTechnicalFailureRequestDto.builder()
                        .errorCode(
                                AutoQuizTechnicalFailureRequestDto
                                        .CODE_AI_OUTPUT_INVALID)
                        .build();

        when(generationRepository.findById(genId))
                .thenReturn(Optional.of(row(genId, token,
                        QuizGenerationStatus.CANCELLED)));

        AutoQuizCallbackAccessDeniedException ex = assertThrows(
                AutoQuizCallbackAccessDeniedException.class,
                () -> service.processTechnicalFailure(
                        genId, token, req));
        // CANCELLED still routes through the GENERATION_NOT_FOUND
        // envelope to keep the caller from probing row existence.
        assertEquals(Reason.GENERATION_NOT_FOUND, ex.reason());
        verify(generationRepository, never())
                .markFailedFromProcessing(any(), any(), any(), any());
    }

    @Test
    @DisplayName("non-whitelisted errorCode: rejected with UNKNOWN_ERROR_CODE")
    void nonWhitelistedErrorCodeRejected() {
        UUID genId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        AutoQuizTechnicalFailureRequestDto req =
                AutoQuizTechnicalFailureRequestDto.builder()
                        .errorCode("CUSTOM_NOT_WHITELISTED")
                        .build();

        when(generationRepository.findById(genId))
                .thenReturn(Optional.of(row(genId, token,
                        QuizGenerationStatus.PROCESSING)));

        AutoQuizCallbackAccessDeniedException ex = assertThrows(
                AutoQuizCallbackAccessDeniedException.class,
                () -> service.processTechnicalFailure(
                        genId, token, req));
        assertEquals(Reason.UNKNOWN_ERROR_CODE, ex.reason());
        verify(generationRepository, never())
                .markFailedFromProcessing(any(), any(), any(), any());
    }

    @Test
    @DisplayName("missing errorCode: rejected with UNKNOWN_ERROR_CODE")
    void missingErrorCodeRejected() {
        UUID genId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        AutoQuizTechnicalFailureRequestDto req =
                AutoQuizTechnicalFailureRequestDto.builder()
                        .message("no errorCode at all")
                        .build();

        when(generationRepository.findById(genId))
                .thenReturn(Optional.of(row(genId, token,
                        QuizGenerationStatus.PROCESSING)));

        AutoQuizCallbackAccessDeniedException ex = assertThrows(
                AutoQuizCallbackAccessDeniedException.class,
                () -> service.processTechnicalFailure(
                        genId, token, req));
        assertEquals(Reason.UNKNOWN_ERROR_CODE, ex.reason());
    }

    @Test
    @DisplayName("missing token: rejected with MISSING_TOKEN")
    void missingTokenRejected() {
        UUID genId = UUID.randomUUID();
        AutoQuizTechnicalFailureRequestDto req =
                AutoQuizTechnicalFailureRequestDto.builder()
                        .errorCode(
                                AutoQuizTechnicalFailureRequestDto
                                        .CODE_AI_OUTPUT_INVALID)
                        .build();

        // Generation row is never loaded because the token guard
        // fires first.
        AutoQuizCallbackAccessDeniedException ex = assertThrows(
                AutoQuizCallbackAccessDeniedException.class,
                () -> service.processTechnicalFailure(
                        genId, null, req));
        assertEquals(Reason.MISSING_TOKEN, ex.reason());
        verify(generationRepository, never()).findById(any());
    }

    @Test
    @DisplayName("missing generation row: rejected with GENERATION_NOT_FOUND")
    void missingGenerationRejected() {
        UUID genId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        AutoQuizTechnicalFailureRequestDto req =
                AutoQuizTechnicalFailureRequestDto.builder()
                        .errorCode(
                                AutoQuizTechnicalFailureRequestDto
                                        .CODE_AI_OUTPUT_INVALID)
                        .build();

        when(generationRepository.findById(genId))
                .thenReturn(Optional.empty());

        AutoQuizCallbackAccessDeniedException ex = assertThrows(
                AutoQuizCallbackAccessDeniedException.class,
                () -> service.processTechnicalFailure(
                        genId, token, req));
        assertEquals(Reason.GENERATION_NOT_FOUND, ex.reason());
    }

    @Test
    @DisplayName("stale token: markFailedFromProcessing returns 0 -> rejected")
    void staleTokenRejected() {
        // The in-memory row holds token X, but the DB UPDATE
        // returns 0 because a concurrent writer has rotated the
        // lease / cancelled the row. The supplied token X still
        // passes the constant-time comparison (it equals the
        // stored token); the rejection comes from the
        // affectedRows == 0 branch on the @Modifying query.
        UUID genId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        AutoQuizTechnicalFailureRequestDto req =
                AutoQuizTechnicalFailureRequestDto.builder()
                        .errorCode(
                                AutoQuizTechnicalFailureRequestDto
                                        .CODE_AI_OUTPUT_INVALID)
                        .build();

        when(generationRepository.findById(genId))
                .thenReturn(Optional.of(row(genId, token,
                        QuizGenerationStatus.PROCESSING)));
        when(generationRepository.markFailedFromProcessing(
                eq(genId), eq(token), any(String.class),
                any(java.time.LocalDateTime.class)))
                .thenReturn(0);

        AutoQuizCallbackAccessDeniedException ex = assertThrows(
                AutoQuizCallbackAccessDeniedException.class,
                () -> service.processTechnicalFailure(
                        genId, token, req));
        assertEquals(Reason.GENERATION_NOT_FOUND, ex.reason(),
                "Stale token must be rejected with the same "
                        + "envelope as a non-PROCESSING state");
    }

    @Test
    @DisplayName("all three whitelisted codes are accepted")
    void allWhitelistedCodesAccepted() {
        for (String code : new String[]{
                AutoQuizTechnicalFailureRequestDto.CODE_AI_OUTPUT_INVALID,
                AutoQuizTechnicalFailureRequestDto.CODE_AI_SCHEMA_INVALID,
                AutoQuizTechnicalFailureRequestDto.CODE_AI_WORKFLOW_FAILED}) {
            UUID genId = UUID.randomUUID();
            UUID token = UUID.randomUUID();
            AutoQuizTechnicalFailureRequestDto req =
                    AutoQuizTechnicalFailureRequestDto.builder()
                            .errorCode(code)
                            .build();

            when(generationRepository.findById(genId))
                    .thenReturn(Optional.of(row(genId, token,
                            QuizGenerationStatus.PROCESSING)));
            when(generationRepository.markFailedFromProcessing(
                    eq(genId), eq(token), any(String.class),
                    any(java.time.LocalDateTime.class)))
                    .thenReturn(1);

            var response = service.processTechnicalFailure(
                    genId, token, req);
            assertNotNull(response);
            assertEquals(true, response.isAccepted());
            assertEquals("FAILED", response.getStatus());
        }
    }
}