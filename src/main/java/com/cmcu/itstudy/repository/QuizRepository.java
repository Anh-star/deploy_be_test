package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.Quiz;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    /**
     * Phase 6C — pessimistic-write lock used by the owner-initiated
     * delete path. The {@link com.cmcu.itstudy.service.contract.QuizGenerationService#deleteForOwner}
     * flow holds this lock between the
     * {@link com.cmcu.itstudy.repository.QuizAttemptRepository#existsByQuiz_Id}
     * check and the actual {@code quizRepository.delete(quiz)} call so
     * that a concurrent {@code /quizzes/{quizId}/start} cannot create
     * a new {@code QuizAttempt} after the count check returned zero.
     *
     * @param quizId id of the quiz row to lock
     * @return the locked row, or empty if it does not exist
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select q
            from Quiz q
            where q.id = :quizId
            """)
    Optional<Quiz> findByIdForUpdate(@Param("quizId") UUID quizId);
}