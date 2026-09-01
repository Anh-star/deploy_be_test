package com.cmcu.itstudy.dto.community;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostCommentRequestDto {

    @NotBlank(message = "Comment body is required")
    private String body;

    /** Null for root comment, set to parent comment ID for reply */
    private String parentCommentId;

    private java.util.List<String> imageUrls;
}
