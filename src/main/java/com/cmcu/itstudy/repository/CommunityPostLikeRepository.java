package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, UUID> {

    Optional<CommunityPostLike> findByPost_IdAndUser_Id(UUID postId, UUID userId);

    long countByPost_IdAndVoteType(UUID postId, String voteType);

    void deleteByPostId(UUID postId);
}
