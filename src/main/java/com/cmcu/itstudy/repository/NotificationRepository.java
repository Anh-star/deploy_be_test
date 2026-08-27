package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :recipientId AND n.read = false")
    void markAllAsReadByRecipientId(@Param("recipientId") UUID recipientId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.referenceId LIKE %:pattern%")
    void deleteByReferenceIdContaining(@Param("pattern") String pattern);

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.referenceType = 'COMMUNITY_POST' AND n.referenceId LIKE :postIdPattern AND n.read = false ORDER BY n.createdAt DESC")
    java.util.List<Notification> findUnreadCommunityPostNotifications(
            @Param("recipientId") UUID recipientId,
            @Param("postIdPattern") String postIdPattern
    );

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.referenceType = 'COMMUNITY_POST' AND n.referenceId LIKE :postIdPattern ORDER BY n.createdAt DESC")
    java.util.List<Notification> findAllCommunityPostCommentNotifications(
            @Param("recipientId") UUID recipientId,
            @Param("postIdPattern") String postIdPattern
    );

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.referenceType = 'COMMUNITY_POST' AND n.type = 'POST_UPVOTED' AND n.referenceId = :referenceId AND n.read = false ORDER BY n.createdAt DESC")
    java.util.List<Notification> findUnreadPostUpvoteNotifications(
            @Param("recipientId") UUID recipientId,
            @Param("referenceId") String referenceId
    );

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.referenceType = 'COMMUNITY_POST' AND n.type = 'POST_UPVOTED' AND n.referenceId = :referenceId ORDER BY n.createdAt DESC")
    java.util.List<Notification> findAllPostUpvoteNotifications(
            @Param("recipientId") UUID recipientId,
            @Param("referenceId") String referenceId
    );

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.referenceType = 'DOCUMENT' AND n.referenceId LIKE :docIdPattern ORDER BY n.createdAt DESC")
    java.util.List<Notification> findAllDocumentCommentNotifications(
            @Param("recipientId") UUID recipientId,
            @Param("docIdPattern") String docIdPattern
    );

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.referenceType = 'DOCUMENT' AND n.type = 'DOCUMENT_PURCHASED' AND n.referenceId = :referenceId ORDER BY n.createdAt DESC")
    java.util.List<Notification> findAllDocumentPurchaseNotifications(
            @Param("recipientId") UUID recipientId,
            @Param("referenceId") String referenceId
    );
}
