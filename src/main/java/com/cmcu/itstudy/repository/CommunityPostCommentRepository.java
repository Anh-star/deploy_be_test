package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommunityPostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CommunityPostCommentRepository extends JpaRepository<CommunityPostComment, UUID> {

    Page<CommunityPostComment> findByPost_IdAndParentIsNullAndDeletedFalseOrderByCreatedAtDesc(UUID postId, Pageable pageable);

    List<CommunityPostComment> findByParent_IdAndDeletedFalseOrderByCreatedAtAsc(UUID parentId);

    long countByPost_IdAndDeletedFalse(UUID postId);

    List<CommunityPostComment> findByPost_Id(UUID postId);

    List<CommunityPostComment> findByParent_Id(UUID parentId);

    List<CommunityPostComment> findByDeletedTrueAndDeletedAtBefore(LocalDateTime threshold);

    void deleteByPostId(UUID postId);
}
