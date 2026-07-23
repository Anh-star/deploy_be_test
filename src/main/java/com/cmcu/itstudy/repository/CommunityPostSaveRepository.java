package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityPostSaveRepository extends JpaRepository<CommunityPostSave, UUID> {

    Optional<CommunityPostSave> findByPost_IdAndUser_Id(UUID postId, UUID userId);

    Page<CommunityPostSave> findByUser_IdOrderBySavedAtDesc(UUID userId, Pageable pageable);

    void deleteByPostId(UUID postId);

    @Query("SELECT s.post.id FROM CommunityPostSave s WHERE s.post.id IN :postIds AND s.user.id = :userId")
    List<UUID> findSavedPostIds(@Param("postIds") List<UUID> postIds, @Param("userId") UUID userId);
}
