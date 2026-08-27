package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommunityPostEditHistoryRepository extends JpaRepository<CommunityPostEditHistory, UUID> {

    @Query("SELECT h FROM CommunityPostEditHistory h LEFT JOIN FETCH h.editor WHERE h.post.id = :postId ORDER BY h.editedAt DESC")
    List<CommunityPostEditHistory> findByPostIdOrderByEditedAtDesc(@Param("postId") UUID postId);

    @Query("SELECT COUNT(h) FROM CommunityPostEditHistory h WHERE h.post.id = :postId")
    long countByPostId(@Param("postId") UUID postId);
}
