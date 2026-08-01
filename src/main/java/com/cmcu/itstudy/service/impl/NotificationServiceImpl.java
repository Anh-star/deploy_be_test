package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.notification.NotificationResponseDto;
import com.cmcu.itstudy.entity.Notification;
import com.cmcu.itstudy.entity.User;
import com.cmcu.itstudy.enums.NotificationType;
import com.cmcu.itstudy.repository.NotificationRepository;
import com.cmcu.itstudy.repository.UserRepository;
import com.cmcu.itstudy.service.contract.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseService sseService;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            SseService sseService
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.sseService = sseService;
    }

    @Override
    @Transactional
    public void createAndPush(
            UUID recipientId,
            UUID actorId,
            NotificationType type,
            String referenceId,
            String referenceType,
            String message
    ) {
        // Do not notify self
        if (actorId != null && Objects.equals(recipientId, actorId)) {
            return;
        }

        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }

        User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;

        Notification notification = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .message(message)
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponseDto dto = mapToDto(saved);

        // Real-time push via SSE
        sseService.pushEvent(recipientId, "notification", dto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getNotifications(UUID userId, int page, int size) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notif = notificationRepository.findById(notificationId).orElse(null);
        if (notif != null && notif.getRecipient().getId().equals(userId)) {
            notif.setRead(true);
            notificationRepository.save(notif);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
    }

    private NotificationResponseDto mapToDto(Notification notif) {
        User actor = notif.getActor();
        return NotificationResponseDto.builder()
                .id(notif.getId().toString())
                .actorId(actor != null ? actor.getId().toString() : null)
                .actorName(actor != null ? actor.getFullName() : "Hệ thống")
                .actorAvatar(actor != null ? actor.getAvatarUrl() : null)
                .type(notif.getType())
                .referenceId(notif.getReferenceId())
                .referenceType(notif.getReferenceType())
                .message(notif.getMessage())
                .isRead(notif.getRead())
                .createdAt(notif.getCreatedAt())
                .build();
    }
}
