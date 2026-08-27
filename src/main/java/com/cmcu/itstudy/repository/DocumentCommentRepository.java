package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentCommentRepository extends JpaRepository<DocumentComment, UUID> {

    long countByDocument_IdAndDeletedFalse(UUID documentId);

    long countByParent_IdAndDeletedFalse(UUID commentId);

    default long countByDocumentId(UUID documentId) {
        return countByDocument_IdAndDeletedFalse(documentId);
    }

    @EntityGraph(attributePaths = {"author", "replyToUser"})
    Page<DocumentComment> findByDocument_IdAndDeletedFalseAndParentIsNullOrderByLikeCountDescCreatedAtDesc(
            UUID documentId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"author", "replyToUser"})
    List<DocumentComment> findByParent_IdAndDeletedFalseOrderByCreatedAtAsc(UUID parentId);

    @EntityGraph(attributePaths = {"document", "author"})
    @Query("select c from DocumentComment c where c.id = :id and c.deleted = false")
    Optional<DocumentComment> findByIdWithDocumentAndAuthor(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"document", "author", "replyToUser"})
    @Query("select c from DocumentComment c where c.id = :id and c.deleted = false")
    Optional<DocumentComment> findByIdWithDocumentAuthorAndReplyTo(@Param("id") UUID id);

    @Query("""
            select c.parent.id, count(c)
            from DocumentComment c
            where c.parent.id in :ids
              and c.deleted = false
            group by c.parent.id
            """)
    List<Object[]> countDirectRepliesByParentIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            select c
            from DocumentComment c
            join fetch c.author
            where c.document.id = :documentId
              and c.deleted = false
              and c.pinned = true
            order by c.createdAt desc
            """)
    List<DocumentComment> findPinnedWithAuthor(@Param("documentId") UUID documentId, Pageable pageable);

    @Query("""
            select c
            from DocumentComment c
            join fetch c.author
            where c.document.id = :documentId
              and c.deleted = false
              and c.parent is null
            order by c.createdAt desc
            """)
    List<DocumentComment> findTop5ByDocumentIdOrderByCreatedAtDesc(@Param("documentId") UUID documentId, Pageable pageable);

    @Query("SELECT u.fullName FROM DocumentComment c JOIN c.author u WHERE c.document.id = :documentId AND u.id != :excludedUserId AND (c.deleted = false OR c.deleted IS NULL) GROUP BY u.id, u.fullName ORDER BY MAX(c.createdAt) DESC")
    List<String> findDistinctCommenterNamesByDocumentOrderedByRecent(
            @Param("documentId") UUID documentId,
            @Param("excludedUserId") UUID excludedUserId
    );

    @Query("SELECT u.fullName FROM DocumentComment c JOIN c.author u WHERE c.parent.id = :parentId AND u.id != :excludedUserId AND (c.deleted = false OR c.deleted IS NULL) GROUP BY u.id, u.fullName ORDER BY MAX(c.createdAt) DESC")
    List<String> findDistinctReplierNamesByParentCommentOrderedByRecent(
            @Param("parentId") UUID parentId,
            @Param("excludedUserId") UUID excludedUserId
    );
}
