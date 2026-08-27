package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CommunityPostCommentRepository extends JpaRepository<CommunityPostComment, UUID> {

    @Query(
        value = "SELECT c FROM CommunityPostComment c LEFT JOIN FETCH c.author WHERE c.post.id = :postId AND c.parent.id IS NULL AND (c.deleted = false OR c.deleted IS NULL) ORDER BY c.createdAt DESC",
        countQuery = "SELECT COUNT(c) FROM CommunityPostComment c WHERE c.post.id = :postId AND c.parent.id IS NULL AND (c.deleted = false OR c.deleted IS NULL)"
    )
    Page<CommunityPostComment> findRootCommentsByPostId(@Param("postId") UUID postId, Pageable pageable);

    @Query("SELECT c FROM CommunityPostComment c LEFT JOIN FETCH c.author WHERE c.parent.id = :parentId AND (c.deleted = false OR c.deleted IS NULL) ORDER BY c.createdAt ASC")
    List<CommunityPostComment> findRepliesByParentId(@Param("parentId") UUID parentId);

    @Query("SELECT COUNT(c) FROM CommunityPostComment c WHERE c.parent.id = :parentId AND (c.deleted = false OR c.deleted IS NULL)")
    long countRepliesByParentId(@Param("parentId") UUID parentId);

    Page<CommunityPostComment> findByPost_IdAndParentIsNullAndDeletedFalseOrderByCreatedAtDesc(UUID postId, Pageable pageable);

    List<CommunityPostComment> findByParent_IdAndDeletedFalseOrderByCreatedAtAsc(UUID parentId);

    long countByPost_IdAndDeletedFalse(UUID postId);

    List<CommunityPostComment> findByPost_Id(UUID postId);

    List<CommunityPostComment> findByParent_Id(UUID parentId);

    List<CommunityPostComment> findByDeletedTrueAndDeletedAtBefore(LocalDateTime threshold);

    void deleteByPostId(UUID postId);

    @Query("SELECT u.fullName FROM CommunityPostComment c JOIN c.author u WHERE c.post.id = :postId AND u.id != :excludedUserId AND (c.deleted = false OR c.deleted IS NULL) GROUP BY u.id, u.fullName ORDER BY MAX(c.createdAt) DESC")
    List<String> findDistinctCommenterNamesByPostOrderedByRecent(
            @Param("postId") UUID postId,
            @Param("excludedUserId") UUID excludedUserId
    );
}
