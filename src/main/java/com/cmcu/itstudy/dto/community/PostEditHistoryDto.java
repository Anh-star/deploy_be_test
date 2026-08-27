package com.cmcu.itstudy.dto.community;

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
public class PostEditHistoryDto {
    private String id;
    private String postId;
    private String editorId;
    private String editorName;
    private String editorAvatar;
    private String title;
    private String content;
    private LocalDateTime editedAt;
}
