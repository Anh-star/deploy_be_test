package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentQuiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentQuizRepository extends JpaRepository<DocumentQuiz, UUID> {

    /**
     * Phase 6C — guarded bulk delete by documentId + quizId. Used by
     * {@code QuizGenerationServiceImpl.deleteForOwner} to drop the
     * {@code tbl_document_quizzes} row that the auto-generated
     * {@code Quiz} had inherited from the source document. The query
     * is scoped to BOTH ids so a Quiz that is still linked from
     * another document (multi-document edge case) keeps its other
     * mappings and only the relevant row is removed.
     *
     * @return the number of {@code DocumentQuiz} rows actually removed
     *         (0 or 1 in normal operation)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from DocumentQuiz dq
            where dq.document.id = :documentId
              and dq.quiz.id = :quizId
            """)
    int deleteByDocument_IdAndQuiz_Id(
            @Param("documentId") UUID documentId,
            @Param("quizId") UUID quizId);

    @Query("""
            select dq
            from DocumentQuiz dq
            join fetch dq.quiz q
            where dq.document.id = :documentId
            order by dq.sortOrder asc, dq.id asc
            """)
    List<DocumentQuiz> findAllByDocumentIdWithQuiz(@Param("documentId") UUID documentId);

    @Query(
            value = """
                    select dq
                    from DocumentQuiz dq
                    join fetch dq.quiz q
                    where dq.document.id = :documentId
                    """,
            countQuery = """
                    select count(dq)
                    from DocumentQuiz dq
                    where dq.document.id = :documentId
                    """
    )
    Page<DocumentQuiz> findByDocumentIdWithQuiz(@Param("documentId") UUID documentId, Pageable pageable);

    @Query("""
            select dq
            from DocumentQuiz dq
            join fetch dq.document d
            left join fetch dq.quiz q
            left join fetch q.questions
            where dq.quiz.id = :quizId
              and d.deleted = false
            """)
    List<DocumentQuiz> findAllByQuizIdWithDocument(@Param("quizId") UUID quizId);

    @Query(
            value = """
            select dq
            from DocumentQuiz dq
            join fetch dq.quiz q
            join fetch dq.document d
            left join fetch d.createdBy
            where d.createdBy.id = :userId
              and d.deleted = false
            order by dq.sortOrder asc, dq.id asc
            """,
            countQuery = """
            select count(dq)
            from DocumentQuiz dq
            join dq.document d
            where d.createdBy.id = :userId
              and d.deleted = false
            """)
    org.springframework.data.domain.Page<DocumentQuiz> findByOwnerIdWithQuizAndDocumentPaged(
            @Param("userId") UUID userId,
            org.springframework.data.domain.Pageable pageable);
}
