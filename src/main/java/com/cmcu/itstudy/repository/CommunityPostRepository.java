package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    @Query(
        value = "SELECT p FROM CommunityPost p LEFT JOIN FETCH p.author WHERE p.deleted = false ORDER BY p.createdAt DESC",
        countQuery = "SELECT COUNT(p) FROM CommunityPost p WHERE p.deleted = false"
    )
    Page<CommunityPost> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    long countByDeletedFalse();

    @Query("SELECT p FROM CommunityPost p LEFT JOIN FETCH p.author WHERE p.id = :id AND p.deleted = false")
    Optional<CommunityPost> findByIdWithAuthor(@Param("id") UUID id);

    @Query("SELECT p.id FROM CommunityPostLike pl JOIN pl.post p WHERE p.id IN :postIds AND pl.user.id = :userId")
    List<UUID> findLikedPostIds(@Param("postIds") List<UUID> postIds, @Param("userId") UUID userId);

    List<CommunityPost> findByDeletedTrueAndDeletedAtBefore(LocalDateTime threshold);
}
