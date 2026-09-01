package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.CommentEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentEditHistoryRepository extends JpaRepository<CommentEditHistory, UUID> {

    List<CommentEditHistory> findByCommentIdAndCommentTypeOrderByEditedAtDesc(UUID commentId, String commentType);
}
