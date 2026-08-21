package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.QuizQuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface QuizQuestionOptionRepository extends JpaRepository<QuizQuestionOption, UUID> {

    /**
     * Phase 6C — bulk delete every {@code QuizQuestionOption} whose
     * parent {@code QuizQuestion} belongs to the supplied
     * {@code quizId}. Used by
     * {@code QuizGenerationServiceImpl.deleteForOwner} to satisfy the
     * {@code tbl_quiz_question_options.question_id} FK before the
     * questions are deleted by
     * {@link QuizQuestionRepository#deleteByQuiz_Id}.
     *
     * @return number of {@code QuizQuestionOption} rows actually removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from QuizQuestionOption qo
            where qo.question.quiz.id = :quizId
            """)
    int deleteByQuestion_Quiz_Id(@Param("quizId") UUID quizId);
}