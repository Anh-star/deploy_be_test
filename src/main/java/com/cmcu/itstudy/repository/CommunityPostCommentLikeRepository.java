package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityPostCommentLikeRepository extends JpaRepository<CommunityPostCommentLike, UUID> {

    Optional<CommunityPostCommentLike> findByComment_IdAndUser_Id(UUID commentId, UUID userId);

    @Query("SELECT l.comment.id FROM CommunityPostCommentLike l WHERE l.comment.id IN :commentIds AND l.user.id = :userId")
    List<UUID> findLikedCommentIds(@Param("commentIds") List<UUID> commentIds, @Param("userId") UUID userId);

    void deleteByCommentId(UUID commentId);
}
