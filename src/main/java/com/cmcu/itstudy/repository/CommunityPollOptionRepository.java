package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommunityPollOptionRepository extends JpaRepository<CommunityPollOption, UUID> {

    List<CommunityPollOption> findByPoll_IdOrderByDisplayOrderAsc(UUID pollId);
}
