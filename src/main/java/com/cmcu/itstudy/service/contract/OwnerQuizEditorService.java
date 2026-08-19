package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.quiz.OwnerQuizEditorRequestDto;
import com.cmcu.itstudy.dto.quiz.OwnerQuizEditorResponseDto;

import java.util.UUID;

/**
 * Owner-only quiz editor service.
 *
 * <p>Only the document owner (the user that owns the {@code Document}
 * whose {@code QuizGeneration} produced the {@link com.cmcu.itstudy.entity.Quiz})
 * is permitted to read or mutate the quiz content via this contract.</p>
 *
 * <p>Public preview endpoints ({@code /api/quizzes/{quizId}/preview}) MUST
 * continue to use {@link QuizService#getQuizPreview(UUID)} which never exposes
 * {@code isCorrect}.</p>
 */
public interface OwnerQuizEditorService {

    /**
     * Returns the full editor payload for {@code quizId}.
     *
     * @throws java.util.NoSuchElementException if the quiz does not exist
     *         (404) or is not associated with any document the caller owns
     *         (also surfaced as 404 to avoid leaking existence)
     * @throws org.springframework.security.access.AccessDeniedException
     *         if the caller is logged in but is not the owner (403)
     */
    OwnerQuizEditorResponseDto getOwnerQuizEditor(UUID quizId);

    /**
     * Replaces the editable subset of {@code quizId} (title, description,
     * duration, passScore, questions, options) atomically with the supplied
     * payload.
     *
     * <p>Structural rules enforced server-side:</p>
     * <ul>
     *   <li>Each question must have between 2 and N options.</li>
     *   <li>Exactly one option per question must be marked correct.</li>
     *   <li>{@code questionId} / {@code optionId} values that belong to a
     *       different quiz are rejected as a security violation.</li>
     * </ul>
     *
     * <p>Attempt safety: if the quiz has at least one
     * {@link com.cmcu.itstudy.entity.QuizAttempt} (submitted or
     * in-progress) the caller may still edit text fields, the correct
     * answer, and reorder; <em>deleting</em> an existing question or
     * option is rejected to preserve attempt history.</p>
     */
    OwnerQuizEditorResponseDto saveOwnerQuizEditor(UUID quizId, OwnerQuizEditorRequestDto request);
}