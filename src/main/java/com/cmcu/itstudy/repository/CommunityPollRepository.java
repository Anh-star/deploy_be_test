package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPoll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommunityPollRepository extends JpaRepository<CommunityPoll, UUID> {

    Optional<CommunityPoll> findByPost_Id(UUID postId);

    void deleteByPostId(UUID postId);
}
