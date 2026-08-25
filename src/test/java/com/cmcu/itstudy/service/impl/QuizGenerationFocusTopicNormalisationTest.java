package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.Quiz;
import com.cmcu.itstudy.entity.QuizAttempt;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.entity.QuizQuestion;
import com.cmcu.itstudy.entity.QuizQuestionOption;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentQuizRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.QuizAttemptRepository;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.repository.QuizQuestionOptionRepository;
import com.cmcu.itstudy.repository.QuizQuestionRepository;
import com.cmcu.itstudy.repository.QuizRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7A.1: pins the focus-topic normalisation contract declared in
 * {@link QuizGenerationServiceImpl#enqueueForDocument} via the
 * public service entry point.
 *
 * <p>Behaviour matrix per spec section 8:</p>
 * <ol>
 *   <li>{@code null} → persisted {@code focusTopic == null};</li>
 *   <li>{@code ""} → persisted {@code focusTopic == null};</li>
 *   <li>{@code "   "} → persisted {@code focusTopic == null};</li>
 *   <li>{@code "  Chương 1  "} → persisted {@code focusTopic == "Chương 1"};</li>
 *   <li>{@code length > 500} → {@link IllegalArgumentException}.</li>
 * </ol>
 *
 * <p>The contract is observed through the captured
 * {@link QuizGeneration} argument of
 * {@link QuizGenerationRepository#saveAndFlush(Object)}; this avoids any
 * reflection or visibility relaxation of the production source.</p>
 */
class QuizGenerationFocusTopicNormalisationTest {

    private QuizGenerationRepository quizGenerationRepository;
    private DocumentRepository documentRepository;
    private DocumentFileRepository documentFileRepository;
    private QuizRepository quizRepository;
    private QuizAttemptRepository quizAttemptRepository;
    private DocumentQuizRepository documentQuizRepository;
    private QuizQuestionRepository quizQuestionRepository;
    private QuizQuestionOptionRepository quizQuestionOptionRepository;
    private QuizGenerationServiceImpl service;

    private UUID documentId;
    private UUID documentFileId;

    @BeforeEach
    void setUp() {
        quizGenerationRepository = mock(QuizGenerationRepository.class);
        documentRepository = mock(DocumentRepository.class);
        documentFileRepository = mock(DocumentFileRepository.class);
        quizRepository = mock(QuizRepository.class);
        quizAttemptRepository = mock(QuizAttemptRepository.class);
        documentQuizRepository = mock(DocumentQuizRepository.class);
        quizQuestionRepository = mock(QuizQuestionRepository.class);
        quizQuestionOptionRepository = mock(QuizQuestionOptionRepository.class);

        service = new QuizGenerationServiceImpl(
                quizGenerationRepository,
                documentRepository,
                documentFileRepository,
                quizRepository,
                quizAttemptRepository,
                documentQuizRepository,
                quizQuestionRepository,
                quizQuestionOptionRepository);

        documentId = UUID.randomUUID();
        documentFileId = UUID.randomUUID();

        Document document = new Document();
        document.setId(documentId);
        User owner = new User();
        owner.setId(UUID.randomUUID());
        document.setCreatedBy(owner);

        DocumentFile documentFile = new DocumentFile();
        documentFile.setDocument(document);
        documentFile.setFileExtension("pdf");

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(document));
        when(documentFileRepository.findById(documentFileId))
                .thenReturn(Optional.of(documentFile));
    }

    @Test
    @DisplayName("PDF + null focus → persisted focusTopic is null")
    void nullFocusStaysNull() {
        QuizGeneration saved = QuizGeneration.builder()
                .id(UUID.randomUUID())
                .build();
        when(quizGenerationRepository.saveAndFlush(any(QuizGeneration.class)))
                .thenReturn(saved);

        QuizGeneration result = service.enqueueForDocument(
                documentId, documentFileId,
                AllowedDocumentFileType.PDF,
                10, /* focusTopic = */ null,
                LocalDateTime.now());

        QuizGeneration persisted = capturePersisted();
        assertNotNull(persisted);
        assertNull(persisted.getFocusTopic(),
                "null input must persist as null focusTopic");
        assertEquals(QuizGenerationStatus.QUEUED, persisted.getStatus());
        assertNotNull(result);
    }

    @Test
    @DisplayName("PDF + \"\" focus → persisted focusTopic is null")
    void emptyStringFocusNormalisedToNull() {
        QuizGeneration saved = QuizGeneration.builder()
                .id(UUID.randomUUID())
                .build();
        when(quizGenerationRepository.saveAndFlush(any(QuizGeneration.class)))
                .thenReturn(saved);

        service.enqueueForDocument(
                documentId, documentFileId,
                AllowedDocumentFileType.PDF,
                10, /* focusTopic = */ "",
                LocalDateTime.now());

        QuizGeneration persisted = capturePersisted();
        assertNull(persisted.getFocusTopic(),
                "empty string must normalise to null focusTopic");
    }

    @Test
    @DisplayName("PDF + \"   \" focus → persisted focusTopic is null")
    void whitespaceOnlyFocusNormalisedToNull() {
        QuizGeneration saved = QuizGeneration.builder()
                .id(UUID.randomUUID())
                .build();
        when(quizGenerationRepository.saveAndFlush(any(QuizGeneration.class)))
                .thenReturn(saved);

        service.enqueueForDocument(
                documentId, documentFileId,
                AllowedDocumentFileType.PDF,
                10, /* focusTopic = */ "   ",
                LocalDateTime.now());

        QuizGeneration persisted = capturePersisted();
        assertNull(persisted.getFocusTopic(),
                "whitespace-only must normalise to null focusTopic");
    }

    @Test
    @DisplayName("PDF + \"  Chương 1  \" focus → persisted focusTopic is \"Chương 1\"")
    void nonBlankFocusIsTrimmed() {
        QuizGeneration saved = QuizGeneration.builder()
                .id(UUID.randomUUID())
                .build();
        when(quizGenerationRepository.saveAndFlush(any(QuizGeneration.class)))
                .thenReturn(saved);

        service.enqueueForDocument(
                documentId, documentFileId,
                AllowedDocumentFileType.PDF,
                10, /* focusTopic = */ "  Chương 1  ",
                LocalDateTime.now());

        QuizGeneration persisted = capturePersisted();
        assertEquals("Chương 1", persisted.getFocusTopic(),
                "non-blank input must be trimmed");
    }

    @Test
    @DisplayName("PDF + 500-char focus → persisted verbatim, no exception")
    void boundaryLengthPreserved() {
        QuizGeneration saved = QuizGeneration.builder()
                .id(UUID.randomUUID())
                .build();
        when(quizGenerationRepository.saveAndFlush(any(QuizGeneration.class)))
                .thenReturn(saved);

        String fiveHundred = "a".repeat(500);
        service.enqueueForDocument(
                documentId, documentFileId,
                AllowedDocumentFileType.PDF,
                10, fiveHundred,
                LocalDateTime.now());

        QuizGeneration persisted = capturePersisted();
        assertEquals(fiveHundred, persisted.getFocusTopic(),
                "500-char input must be preserved verbatim");
    }

    @Test
    @DisplayName("PDF + 501-char focus → IllegalArgumentException, no save")
    void overflowRejected() {
        String fiveHundredAndOne = "a".repeat(501);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.enqueueForDocument(
                        documentId, documentFileId,
                        AllowedDocumentFileType.PDF,
                        10, fiveHundredAndOne,
                        LocalDateTime.now()));

        assertTrue(
                ex.getMessage() != null && ex.getMessage().contains("500"),
                "Exception message must reference the 500-char cap; got: "
                        + ex.getMessage());
        // No saveAndFlush must have happened — overflow is a contract
        // violation, the service must NOT persist a truncated row.
        org.mockito.Mockito.verifyNoInteractions(quizGenerationRepository);
    }

    @Test
    @DisplayName("PDF + tabs / mixed whitespace focus → trimmed to non-empty core")
    void mixedWhitespaceTrimming() {
        QuizGeneration saved = QuizGeneration.builder()
                .id(UUID.randomUUID())
                .build();
        when(quizGenerationRepository.saveAndFlush(any(QuizGeneration.class)))
                .thenReturn(saved);

        service.enqueueForDocument(
                documentId, documentFileId,
                AllowedDocumentFileType.PDF,
                10, /* focusTopic = */ "\t x \n",
                LocalDateTime.now());

        QuizGeneration persisted = capturePersisted();
        assertEquals("x", persisted.getFocusTopic(),
                "mixed whitespace must be trimmed to the non-empty core");
    }

    @Test
    @DisplayName("DOCX + null focus → WAITING_SOURCE initial status")
    void docxNullFocusStartsWaitingSource() {
        QuizGeneration saved = QuizGeneration.builder()
                .id(UUID.randomUUID())
                .build();
        when(quizGenerationRepository.saveAndFlush(any(QuizGeneration.class)))
                .thenReturn(saved);

        // Override the default document file extension to docx for this
        // case so the service can resolve AllowedDocumentFileType.DOCX.
        DocumentFile docxFile = documentFileRepository
                .findById(documentFileId)
                .orElseThrow(AssertionError::new);
        docxFile.setFileExtension("docx");
        when(documentFileRepository.findById(documentFileId))
                .thenReturn(Optional.of(docxFile));

        service.enqueueForDocument(
                documentId, documentFileId,
                AllowedDocumentFileType.DOCX,
                20, null,
                LocalDateTime.now());

        QuizGeneration persisted = capturePersisted();
        assertEquals(
                QuizGenerationStatus.WAITING_SOURCE,
                persisted.getStatus(),
                "DOCX must start as WAITING_SOURCE; the source-ready bridge "
                        + "promotes it to QUEUED only after the FULL preview "
                        + "artifact reaches READY");
        assertNull(persisted.getFocusTopic(),
                "DOCX + null focus must persist as null");
    }

    /**
     * Returns the {@link QuizGeneration} that was actually handed to
     * {@code saveAndFlush(...)} so each test can assert the post-
     * normalisation {@code focusTopic} without ever touching the
     * package-private helper.
     */
    private QuizGeneration capturePersisted() {
        ArgumentCaptor<QuizGeneration> captor =
                ArgumentCaptor.forClass(QuizGeneration.class);
        verify(quizGenerationRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}