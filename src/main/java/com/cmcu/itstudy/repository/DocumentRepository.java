package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.Document;
import com.cmcu.itstudy.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    @EntityGraph(attributePaths = {"category", "createdBy"})
    Optional<Document> findByIdAndDeletedFalse(UUID id);

    /**
     * Slug existence check used by the unique-slug resolver.
     *
     * <p>This lookup is INTENTIONALLY not filtered by {@code deleted = false}.
     * The {@code tbl_documents.slug} column carries a plain UNIQUE constraint
     * in the schema, so a soft-deleted row still occupies its slug — a fresh
     * create with the same title must therefore generate a suffixed slug
     * rather than recycle the soft-deleted one's. Per the bug-fix brief the
     * DB constraint is NOT being redesigned in this phase.
     */
    boolean existsBySlug(String slug);

    Optional<Document> findBySlug(String slug);

    /**
     * Slug existence check that excludes the supplied document id. Used by
     * the update path so a document whose title is unchanged does not collide
     * with its own existing slug row.
     */
    boolean existsBySlugAndIdNot(String slug, UUID id);

    @EntityGraph(attributePaths = {"category", "createdBy"})
    @Query("select d from Document d where d.status = :status and d.deleted = false order by d.createdAt desc")
    Page<Document> findPendingPageWithCategoryAndCreator(@Param("status") DocumentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "createdBy"})
    @Query("select d from Document d where d.deleted = false order by d.createdAt desc")
    Page<Document> findAllPageWithCategoryAndCreator(Pageable pageable);

    @EntityGraph(attributePaths = {"category", "createdBy"})
    @Query("SELECT d FROM Document d WHERE d.deleted = false " +
           "AND (:status IS NULL OR d.status = :status) " +
           "AND (:search IS NULL OR :search = '' OR LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR (d.fileName IS NOT NULL AND LOWER(d.fileName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "     OR (d.createdBy.fullName IS NOT NULL AND LOWER(d.createdBy.fullName) LIKE LOWER(CONCAT('%', :search, '%')))) " +
           "AND (:startDate IS NULL OR d.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR d.createdAt <= :endDate) " +
           "ORDER BY d.createdAt DESC")
    Page<Document> searchPendingDocumentsForAdmin(
            @Param("status") DocumentStatus status,
            @Param("search") String search,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    Page<Document> findByStatusAndDeletedFalseOrderByCreatedAtDesc(DocumentStatus status, Pageable pageable);

    Page<Document> findByStatusOrderByCreatedAtDesc(DocumentStatus status, Pageable pageable);

    Page<Document> findByStatusAndDeletedFalseOrderByViewCountDescDownloadCountDescCreatedAtDesc(DocumentStatus status, Pageable pageable);

    Page<Document> findByStatusOrderByViewCountDescDownloadCountDescCreatedAtDesc(DocumentStatus status, Pageable pageable);

    long countByStatusAndDeletedFalse(DocumentStatus status);

    long countByDeletedFalse();

    @Query("""
            select count(d) from Document d
            where d.deleted = false and d.createdAt >= :from and d.createdAt < :to
            """)
    long countCreatedBetweenNotDeleted(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countByStatusAndDeletedFalseAndCreatedByIsNotNull(DocumentStatus status);

    @Query("""
            select coalesce(sum(d.downloadCount), 0)
            from Document d
            where d.status = :status
              and d.deleted = false
            """)
    long sumDownloadCountByStatusAndDeletedFalse(@Param("status") DocumentStatus status);

    @Query("""
            select d
            from Document d
            where d.status = :status
              and d.deleted = false
              and d.id in (
                  select dt.document.id
                  from DocumentTag dt
                  where dt.tagId in :tagIds
                  group by dt.document.id
                  having count(distinct dt.tagId) = :tagCount
              )
            """)
    Page<Document> findByStatusAndDeletedFalseAndAllTags(@Param("status") DocumentStatus status,
                                                         @Param("tagIds") List<UUID> tagIds,
                                                         @Param("tagCount") long tagCount,
                                                         Pageable pageable);

    @Query("""
            select d
            from Document d
            where d.status = :status
              and d.deleted = false
              and lower(d.title) like lower(concat('%', :keyword, '%'))
            """)
    Page<Document> searchByStatusAndDeletedFalseAndTitleContaining(@Param("status") DocumentStatus status,
                                                                   @Param("keyword") String keyword,
                                                                   Pageable pageable);

    @Query("""
            select d
            from Document d
            where d.status = :status
              and d.deleted = false
              and lower(d.title) like lower(concat('%', :keyword, '%'))
            order by d.createdAt desc
            """)
    Page<Document> searchByStatusAndDeletedFalseAndTitleContainingOrderByCreatedAtDesc(@Param("status") DocumentStatus status,
                                                                                       @Param("keyword") String keyword,
                                                                                       Pageable pageable);

    @Query("""
            select d
            from Document d
            where d.status = :status
              and d.deleted = false
              and d.createdAt >= :from
            order by d.viewCount desc, d.downloadCount desc, d.createdAt desc
            """)
    List<Document> findTrendingDocuments(@Param("status") DocumentStatus status,
                                         @Param("from") LocalDateTime from,
                                         Pageable pageable);

    @Query("""
            select d
            from DocumentView v
            join v.document d
            where d.status = :status
              and d.deleted = false
              and v.viewedAt >= :from
            group by d
            order by count(v) desc, d.createdAt desc
            """)
    List<Document> findTrendingByViews(@Param("status") DocumentStatus status,
                                       @Param("from") LocalDateTime from,
                                       Pageable pageable);

    @Query("""
            select d
            from DocumentDownload v
            join v.document d
            where d.status = :status
              and d.deleted = false
              and v.downloadedAt >= :from
            group by d
            order by count(v) desc, d.createdAt desc
            """)
    List<Document> findTrendingByDownloads(@Param("status") DocumentStatus status,
                                           @Param("from") LocalDateTime from,
                                           Pageable pageable);

    @Query("""
            select d
            from Document d
            where d.status = :status
              and d.deleted = false
              and d.category.id = :categoryId
              and d.id <> :excludeDocumentId
            order by d.viewCount desc, d.downloadCount desc, d.createdAt desc
            """)
    Slice<Document> findRelatedDocumentsForDetail(@Param("status") DocumentStatus status,
                                                  @Param("categoryId") UUID categoryId,
                                                  @Param("excludeDocumentId") UUID excludeDocumentId,
                                                  Pageable pageable);

    @EntityGraph(attributePaths = {"category", "createdBy", "documentTags.tag"})
    List<Document> findByCreatedByAndDeletedFalseOrderByCreatedAtDesc(com.cmcu.itstudy.entity.User createdBy);

    @Query("select d.id, u.id, u.fullName from Document d left join d.createdBy u where d.id in :ids")
    List<Object[]> findUploaderByDocumentIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            select d.createdBy.id,
                   coalesce(sum(d.downloadCount), 0),
                   count(d.id),
                   coalesce(sum(d.viewCount), 0)
            from Document d
            where d.createdBy.id in :userIds
              and d.deleted = false
              and (d.hidden = false or d.hidden is null)
              and upper(d.createdBy.status) = 'ACTIVE'
              and d.status = com.cmcu.itstudy.enums.DocumentStatus.APPROVED
            group by d.createdBy.id
            """)
    List<Object[]> findStatsByUserIds(@Param("userIds") Collection<UUID> userIds);

    @Query(value = """
            select u.id, u.full_name, u.avatar,
                   coalesce(sum(d.view_count), 0) as total_views,
                   coalesce(sum(d.download_count), 0) as total_downloads,
                   count(d.id) as total_documents
            from tbl_users u
            join tbl_documents d on d.created_by = u.id
              and d.status = 'APPROVED'
              and d.is_deleted = 0
              and (d.is_hidden = 0 or d.is_hidden is null)
            where upper(u.status) = 'ACTIVE'
            group by u.id, u.full_name, u.avatar
            order by total_views desc, total_downloads desc, total_documents desc, u.full_name asc
            """, nativeQuery = true)
    List<Object[]> findLeaderboardUsersByViews(Pageable pageable);

    @Query(value = """
            select u.id, u.full_name, u.avatar,
                   coalesce(sum(d.view_count), 0) as total_views,
                   coalesce(sum(d.download_count), 0) as total_downloads,
                   count(d.id) as total_documents
            from tbl_users u
            join tbl_documents d on d.created_by = u.id
              and d.status = 'APPROVED'
              and d.is_deleted = 0
              and (d.is_hidden = 0 or d.is_hidden is null)
            where upper(u.status) = 'ACTIVE'
            group by u.id, u.full_name, u.avatar
            order by total_downloads desc, total_views desc, total_documents desc, u.full_name asc
            """, nativeQuery = true)
    List<Object[]> findLeaderboardUsersByDownloads(Pageable pageable);

    @Query(value = """
            select u.id, u.full_name, u.avatar,
                   coalesce(sum(d.view_count), 0) as total_views,
                   coalesce(sum(case when d.is_paid = 0 then d.download_count else 0 end), 0) as total_downloads,
                   count(case when d.is_paid = 0 then d.id end) as total_documents
            from tbl_users u
            join tbl_documents d on d.created_by = u.id
              and d.status = 'APPROVED'
              and d.is_deleted = 0
              and (d.is_hidden = 0 or d.is_hidden is null)
            where upper(u.status) = 'ACTIVE'
            group by u.id, u.full_name, u.avatar
            having coalesce(sum(case when d.is_paid = 0 then d.download_count else 0 end), 0) > 0
            order by total_downloads desc, total_views desc, total_documents desc, u.full_name asc
            """, nativeQuery = true)
    List<Object[]> findLeaderboardUsersByFreeDownloads(Pageable pageable);

    @Query(value = """
            select u.id, u.full_name, u.avatar,
                   coalesce(sum(d.view_count), 0) as total_views,
                   coalesce(sum(case when d.is_paid = 1 then d.download_count else 0 end), 0) as total_downloads,
                   count(case when d.is_paid = 1 then d.id end) as total_documents
            from tbl_users u
            join tbl_documents d on d.created_by = u.id
              and d.status = 'APPROVED'
              and d.is_deleted = 0
              and (d.is_hidden = 0 or d.is_hidden is null)
            where upper(u.status) = 'ACTIVE'
            group by u.id, u.full_name, u.avatar
            having coalesce(sum(case when d.is_paid = 1 then d.download_count else 0 end), 0) > 0
            order by total_downloads desc, total_views desc, total_documents desc, u.full_name asc
            """, nativeQuery = true)
    List<Object[]> findLeaderboardUsersByPaidDownloads(Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Document d SET d.hidden = true WHERE d.createdBy.id = :userId AND (d.deleted = false OR d.deleted IS NULL)")
    int hideAllByCreatedById(@Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Document d SET d.hidden = false WHERE d.createdBy.id = :userId AND (d.deleted = false OR d.deleted IS NULL)")
    int unhideAllByCreatedById(@Param("userId") UUID userId);
}
