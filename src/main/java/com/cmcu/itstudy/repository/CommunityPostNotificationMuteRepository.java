package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostNotificationMute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommunityPostNotificationMuteRepository extends JpaRepository<CommunityPostNotificationMute, UUID> {

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM CommunityPostNotificationMute m WHERE m.post.id = :postId AND m.user.id = :userId")
    boolean existsByPost_IdAndUser_Id(@Param("postId") UUID postId, @Param("userId") UUID userId);

    @Query("SELECT m FROM CommunityPostNotificationMute m WHERE m.post.id = :postId AND m.user.id = :userId")
    Optional<CommunityPostNotificationMute> findByPost_IdAndUser_Id(@Param("postId") UUID postId, @Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM CommunityPostNotificationMute m WHERE m.post.id = :postId AND m.user.id = :userId")
    void deleteByPost_IdAndUser_Id(@Param("postId") UUID postId, @Param("userId") UUID userId);
}
