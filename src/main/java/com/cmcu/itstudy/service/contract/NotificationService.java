package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.notification.NotificationResponseDto;
import com.cmcu.itstudy.enums.NotificationType;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface NotificationService {

    void createAndPush(
            UUID recipientId,
            UUID actorId,
            NotificationType type,
            String referenceId,
            String referenceType,
            String message
    );

    Page<NotificationResponseDto> getNotifications(UUID userId, int page, int size);

    long getUnreadCount(UUID userId);

    void markAsRead(UUID notificationId, UUID userId);

    void markAllAsRead(UUID userId);
}
