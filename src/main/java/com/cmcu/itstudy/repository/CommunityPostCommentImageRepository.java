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

    List<CommunityPostCommentImage> findByComment_IdOrderByDisplayOrderAsc(UUID commentId);

    List<CommunityPostCommentImage> findByComment_IdInOrderByDisplayOrderAsc(Collection<UUID> commentIds);

    @Modifying
    @Query("delete from CommunityPostCommentImage img where img.comment.id = :commentId")
    void deleteByCommentId(@Param("commentId") UUID commentId);
}
