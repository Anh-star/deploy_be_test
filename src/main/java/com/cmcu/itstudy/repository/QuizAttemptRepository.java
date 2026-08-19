package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.QuizAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    List<QuizAttempt> findByUserIdAndQuizIdOrderByAttemptNumberDesc(UUID userId, UUID quizId);

    Optional<QuizAttempt> findTopByUserIdAndQuizIdAndStatusOrderByStartTimeDesc(UUID userId, UUID quizId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select qa
            from QuizAttempt qa
            where qa.userId = :userId
              and qa.quiz.id = :quizId
              and upper(qa.status) = 'IN_PROGRESS'
              and qa.endTime is null
            order by qa.startTime desc
            """)
    List<QuizAttempt> findInProgressAttemptsForUpdate(@Param("userId") UUID userId, @Param("quizId") UUID quizId);

    long countByUserIdAndQuizIdAndStartTimeBetween(UUID userId, UUID quizId, LocalDateTime startTime, LocalDateTime endTime);

    List<QuizAttempt> findByUserIdOrderByStartTimeDesc(UUID userId);

    Page<QuizAttempt> findByUserId(UUID userId, Pageable pageable);

    long countByUserIdAndStatusIn(UUID userId, Collection<String> statuses);

    long countByUserIdAndStatus(UUID userId, String status);

    boolean existsByQuiz_Id(UUID quizId);

    @Query("""
            select avg(qa.score)
            from QuizAttempt qa
            where qa.userId = :userId
              and upper(qa.status) in ('PASSED', 'FAILED')
              and qa.score is not null
            """)
    Double averageSubmittedScore(@Param("userId") UUID userId);

    @Query("""
            select avg(qa.score)
            from QuizAttempt qa
            where qa.userId = :userId
              and upper(qa.status) in ('PASSED', 'FAILED')
              and qa.score is not null
              and qa.endTime is not null
              and qa.endTime >= :fromInclusive
              and qa.endTime < :toExclusive
            """)
    Double averageSubmittedScoreBetween(
            @Param("userId") UUID userId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Query(value = """
            select cast(qa.end_time as date) as dt, avg(cast(qa.score as float))
            from tbl_quiz_attempts qa
            where qa.user_id = :userId
              and upper(qa.status) in ('PASSED', 'FAILED')
              and qa.end_time is not null
              and qa.score is not null
              and qa.end_time >= :since
            group by cast(qa.end_time as date)
            order by dt asc
            """, nativeQuery = true)
    List<Object[]> findDailyAverageScoresSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    @Query(value = """
            select coalesce(sum(datediff(second, qa.start_time, qa.end_time)), 0)
            from tbl_quiz_attempts qa
            where qa.user_id = :userId
              and qa.end_time is not null
              and qa.start_time is not null
              and qa.end_time >= qa.start_time
            """, nativeQuery = true)
    Number sumDurationSecondsByUser(@Param("userId") UUID userId);

    @Query("""
            select distinct qa
            from QuizAttempt qa
            left join fetch qa.answers aa
            left join fetch aa.question qq
            left join fetch aa.selectedOption so
            where qa.id = :id
            """)
    Optional<QuizAttempt> findByIdWithAnswers(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select qa from QuizAttempt qa where qa.id = :id")
    Optional<QuizAttempt> findByIdForUpdate(@Param("id") UUID id);
}
