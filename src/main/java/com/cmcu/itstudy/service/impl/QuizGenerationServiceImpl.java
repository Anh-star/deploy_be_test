package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.DocumentFile;
import com.cmcu.itstudy.entity.QuizGeneration;
import com.cmcu.itstudy.enums.AllowedDocumentFileType;
import com.cmcu.itstudy.enums.QuizGenerationStatus;
import com.cmcu.itstudy.repository.DocumentFileRepository;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.repository.QuizGenerationRepository;
import com.cmcu.itstudy.service.contract.QuizGenerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link QuizGenerationService} implementation.
 *
 * <p>Phase 2B: this service ONLY writes/reads the
 * {@code tbl_quiz_generations} table. It never makes a remote HTTP call,
 * never signs a Supabase URL, never touches n8n, never schedules
 * anything, and never calls the Gemini client. Those concerns belong to
 * later phases that will read the {@link QuizGeneration} row this service
 * populates.
 *
 * <p>Propagation is {@code MANDATORY}: this bean is always called inside
 * an already-open transaction (the document create / soft-delete path)
 * so its writes participate in the same unit of work as the
 * {@code Document} and {@code DocumentFile} inserts.
 */
@Service
public class QuizGenerationServiceImpl implements QuizGenerationService {

    /**
     * Single-source Vietnamese message used by every V1 Auto Quiz
     * entry point (free, paid, and direct service call) when the
     * uploader picked an unsupported file type. The existing
     * {@code GlobalExceptionHandler} maps {@link IllegalArgumentException}
     * to HTTP 400, so this string is the user-facing 400 payload.
     *
     * <p>Keep this {@code package-private} so the FREE / PAID binders
     * can re-use it without widening the public service surface.
     */
    static final String UNSUPPORTED_AUTO_QUIZ_MESSAGE =
            "Tự động tạo Quiz hiện chỉ hỗ trợ tài liệu PDF, DOC và DOCX.";

    private final QuizGenerationRepository quizGenerationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;

    public QuizGenerationServiceImpl(
            QuizGenerationRepository quizGenerationRepository,
            DocumentRepository documentRepository,
            DocumentFileRepository documentFileRepository) {
        this.quizGenerationRepository = quizGenerationRepository;
        this.documentRepository = documentRepository;
        this.documentFileRepository = documentFileRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public QuizGeneration enqueueForDocument(
            UUID documentId,
            UUID documentFileId,
            AllowedDocumentFileType fileType,
            int requestedQuestionCount,
            LocalDateTime now) {

        // ── Argument validation ─────────────────────────────────────
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (documentFileId == null) {
            throw new IllegalArgumentException("documentFileId must not be null");
        }
        if (fileType == null) {
            throw new IllegalArgumentException(UNSUPPORTED_AUTO_QUIZ_MESSAGE);
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (requestedQuestionCount < 10 || requestedQuestionCount > 50) {
            throw new IllegalArgumentException(
                    "requestedQuestionCount must be in [10, 50]");
        }

        // ── Idempotency: if a row already exists, return it unchanged.
        Optional<QuizGeneration> existing =
                quizGenerationRepository.findByDocument_Id(documentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // ── Initial status mapping by file type ─────────────────────
        QuizGenerationStatus initialStatus;
        switch (fileType) {
            case PDF:
                initialStatus = QuizGenerationStatus.QUEUED;
                break;
            case DOC:
            case DOCX:
                initialStatus = QuizGenerationStatus.WAITING_SOURCE;
                break;
            case PPT:
            case PPTX:
                throw new IllegalArgumentException(UNSUPPORTED_AUTO_QUIZ_MESSAGE);
            default:
                throw new IllegalArgumentException(UNSUPPORTED_AUTO_QUIZ_MESSAGE);
        }

        // ── Load Document + DocumentFile and verify ownership ───────
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Document not found: " + documentId));
        DocumentFile documentFile = documentFileRepository.findById(documentFileId)
                .orElseThrow(() -> new NoSuchElementException(
                        "DocumentFile not found: " + documentFileId));

        if (documentFile.getDocument() == null
                || documentFile.getDocument().getId() == null
                || !documentFile.getDocument().getId().equals(documentId)) {
            throw new IllegalArgumentException(
                    "DocumentFile does not belong to the supplied document");
        }

        // ── Build & persist ─────────────────────────────────────────
        QuizGeneration generation = QuizGeneration.builder()
                .document(document)
                .documentFile(documentFile)
                .requestedQuestionCount(requestedQuestionCount)
                .status(initialStatus)
                .attempts(0)
                .requestedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return quizGenerationRepository.saveAndFlush(generation);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuizGeneration> findByDocumentId(UUID documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        return quizGenerationRepository.findByDocument_Id(documentId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForDocument(UUID documentId, LocalDateTime now) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        Optional<QuizGeneration> existing =
                quizGenerationRepository.findByDocument_Id(documentId);
        if (existing.isEmpty()) {
            return;
        }

        QuizGeneration generation = existing.get();
        QuizGenerationStatus current = generation.getStatus();
        if (current == null) {
            return;
        }

        // READY and CANCELLED are terminal: must not be mutated.
        if (current == QuizGenerationStatus.READY
                || current == QuizGenerationStatus.CANCELLED) {
            return;
        }

        generation.setStatus(QuizGenerationStatus.CANCELLED);
        generation.setCancelledAt(now);
        generation.setNextAttemptAt(null);
        if (generation.getLastError() == null
                || generation.getLastError().isBlank()) {
            generation.setLastError("DOCUMENT_DELETED");
        }

        quizGenerationRepository.saveAndFlush(generation);
    }
}