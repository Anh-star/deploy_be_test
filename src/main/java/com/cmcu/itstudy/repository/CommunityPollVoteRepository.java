package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityPollVoteRepository extends JpaRepository<CommunityPollVote, UUID> {

    Optional<CommunityPollVote> findByPoll_IdAndUser_Id(UUID pollId, UUID userId);

    List<CommunityPollVote> findByPoll_IdAndUser_IdIn(UUID pollId, List<UUID> userIds);

    @Query("SELECT v FROM CommunityPollVote v JOIN FETCH v.option WHERE v.poll.id = :pollId AND v.user.id = :userId")
    List<CommunityPollVote> findAllByPoll_IdAndUser_Id(@Param("pollId") UUID pollId, @Param("userId") UUID userId);

    long countByOption_Id(UUID optionId);

    @Query("SELECT v FROM CommunityPollVote v JOIN FETCH v.user WHERE v.option.id = :optionId")
    List<CommunityPollVote> findByOptionIdWithUser(@Param("optionId") UUID optionId);

    @Modifying
    @Query("DELETE FROM CommunityPollVote v WHERE v.poll.id = :pollId AND v.user.id = :userId")
    void deleteAllByPollIdAndUserId(@Param("pollId") UUID pollId, @Param("userId") UUID userId);
}
