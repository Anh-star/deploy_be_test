package com.cmcu.itstudy.dto.notification;

import com.cmcu.itstudy.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {

    private String id;
    private String actorId;
    private String actorName;
    private String actorAvatar;
    private NotificationType type;
    private String referenceId;
    private String referenceType;
    private String message;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private String action;
}
