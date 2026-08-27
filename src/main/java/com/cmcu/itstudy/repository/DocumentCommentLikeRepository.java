package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.DocumentCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentCommentLikeRepository extends JpaRepository<DocumentCommentLike, UUID> {

    Optional<DocumentCommentLike> findByComment_IdAndUser_Id(UUID commentId, UUID userId);

    long countByComment_IdAndVoteType(UUID commentId, String voteType);

    @Query("select l.comment.id from DocumentCommentLike l where l.comment.id in :commentIds and l.user.id = :userId")
    List<UUID> findLikedCommentIds(@Param("commentIds") Collection<UUID> commentIds, @Param("userId") UUID userId);

    @Query("select l from DocumentCommentLike l where l.comment.id in :commentIds and l.user.id = :userId")
    List<DocumentCommentLike> findAllByCommentIdInAndUserId(@Param("commentIds") Collection<UUID> commentIds, @Param("userId") UUID userId);

    @Query("""
        SELECT u.fullName
        FROM DocumentCommentLike l
        JOIN l.user u
        WHERE l.comment.id = :commentId
          AND l.voteType = 'UPVOTE'
          AND u.id <> :excludeUserId
        GROUP BY u.id, u.fullName
        ORDER BY MAX(l.likedAt) DESC
    """)
    List<String> findUpvoterNamesByCommentOrderedByRecent(
            @Param("commentId") UUID commentId,
            @Param("excludeUserId") UUID excludeUserId
    );

    void deleteByComment_IdAndUser_Id(UUID commentId, UUID userId);
}
