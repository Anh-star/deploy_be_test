package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostCommentImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommunityPostCommentImageRepository extends JpaRepository<CommunityPostCommentImage, UUID> {

    @Query("SELECT img FROM CommunityPostCommentImage img WHERE img.comment.id = :commentId ORDER BY img.displayOrder ASC")
    List<CommunityPostCommentImage> findByCommentIdOrderByDisplayOrderAsc(@Param("commentId") UUID commentId);

    @Query("SELECT img FROM CommunityPostCommentImage img WHERE img.comment.id IN :commentIds ORDER BY img.displayOrder ASC")
    List<CommunityPostCommentImage> findByCommentIdInOrderByDisplayOrderAsc(@Param("commentIds") Collection<UUID> commentIds);

    @Modifying
    @Query("DELETE FROM CommunityPostCommentImage img WHERE img.comment.id = :commentId")
    void deleteByCommentId(@Param("commentId") UUID commentId);
}

