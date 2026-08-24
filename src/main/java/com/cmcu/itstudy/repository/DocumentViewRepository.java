package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentView;
import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentViewRepository extends JpaRepository<DocumentView, Long> {

    boolean existsByDocumentAndUser(Document document, User user);

    Optional<DocumentView> findByDocumentAndUser(Document document, User user);

    @Query("""
            select v.document.id, max(v.viewedAt)
            from DocumentView v
            where v.user.id = :userId
              and v.document.id in :documentIds
            group by v.document.id
            """)
    List<Object[]> findLastViewedAtByUserAndDocumentIds(
            @Param("userId") UUID userId,
            @Param("documentIds") List<UUID> documentIds);

    @Query("""
            select count(distinct v.document.id)
            from DocumentView v
            where v.user is not null and v.user.id = :userId
            """)
    long countDistinctDocumentsViewedByUserId(@Param("userId") UUID userId);

    long countByDocument(Document document);

    long countByDocument_Id(UUID documentId);

    default long countByDocumentId(UUID documentId) {
        return countByDocument_Id(documentId);
    }

    long countByDocumentAndViewedAtAfter(Document document, LocalDateTime from);

    @Query("""
            select count(v)
            from DocumentView v
            where v.document = :document
              and v.viewedAt >= :from
            """)
    long countRecentViews(@Param("document") Document document, @Param("from") LocalDateTime from);

    @Query(value = """
            select cast(v.viewed_at as date) as d, count(distinct v.user_id)
            from tbl_document_views v
            where v.user_id is not null
              and v.viewed_at >= :since
            group by cast(v.viewed_at as date)
            order by d asc
            """, nativeQuery = true)
    List<Object[]> countDistinctUsersByViewDaySince(@Param("since") LocalDateTime since);

    /**
     * Paged distinct documents viewed by a user, ordered by most recently viewed.
     * Each document appears at most once per page.
     *
     * <p>The query uses explicit {@code GROUP BY v.document.id ORDER BY max(v.viewedAt) DESC}
     * so the ordering is deterministic at the SQL level — no Pageable Sort needed.</p>
     *
     * @param userId  the authenticated user id (must be non-null)
     * @param pageable page + size only (order is in the JPQL)
     * @return page of document ids, one row per distinct document
     */
    @Query(value = """
            select v.document.id
            from DocumentView v
            join v.document d
            where v.user is not null
              and v.user.id = :userId
              and d.deleted = false
            group by v.document.id
            order by max(v.viewedAt) desc
            """,
            countQuery = """
            select count(distinct v.document.id)
            from DocumentView v
            join v.document d
            where v.user is not null
              and v.user.id = :userId
              and d.deleted = false
            """)
    Page<UUID> findDistinctDocumentIdsByUserId(
            @Param("userId") UUID userId,
            Pageable pageable);
}
