package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, UUID> {

    Optional<CommunityPostLike> findByPost_IdAndUser_Id(UUID postId, UUID userId);

    long countByPost_IdAndVoteType(UUID postId, String voteType);

    void deleteByPostId(UUID postId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(u.fullName, 'Người dùng') FROM CommunityPostLike pl " +
           "JOIN pl.user u " +
           "WHERE pl.post.id = :postId AND pl.voteType = 'UPVOTE' AND u.id <> :excludeUserId " +
           "ORDER BY pl.likedAt DESC")
    java.util.List<String> findUpvoterNamesByPostOrderedByRecent(
            @org.springframework.data.repository.query.Param("postId") UUID postId,
            @org.springframework.data.repository.query.Param("excludeUserId") UUID excludeUserId
    );
}
