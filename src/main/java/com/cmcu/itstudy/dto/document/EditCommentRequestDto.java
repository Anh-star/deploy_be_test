package com.cmcu.itstudy.dto.document;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditCommentRequestDto {

    @NotBlank(message = "Nội dung bình luận không được để trống")
    private String body;

    private List<String> imageUrls;
}
