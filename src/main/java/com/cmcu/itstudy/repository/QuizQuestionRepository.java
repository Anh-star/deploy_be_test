package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    @Query("""
            select qq.quiz.id, count(qq.id)
            from QuizQuestion qq
            where qq.quiz.id in :quizIds
            group by qq.quiz.id
            """)
    List<Object[]> countQuestionsGroupedByQuizId(@Param("quizIds") Collection<UUID> quizIds);

    @Query("""
            select distinct qq
            from QuizQuestion qq
            left join fetch qq.options qo
            where qq.quiz.id = :quizId
            order by qq.sortOrder asc, qo.sortOrder asc
            """)
    List<QuizQuestion> findAllByQuizIdWithOptions(@Param("quizId") UUID quizId);

    long countByQuiz_Id(UUID quizId);

    /**
     * Phase 6C — bulk delete every {@code QuizQuestion} of the supplied
     * {@code quizId}. The caller MUST have already deleted every
     * {@code tbl_quiz_question_options} row referencing one of those
     * questions (see
     * {@link QuizQuestionOptionRepository#deleteByQuestion_Quiz_Id}),
     * otherwise the {@code tbl_quiz_question_options.question_id} FK
     * will block the SQL DELETE with a constraint violation.
     *
     * @return number of {@code QuizQuestion} rows actually removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from QuizQuestion qq
            where qq.quiz.id = :quizId
            """)
    int deleteByQuiz_Id(@Param("quizId") UUID quizId);
}