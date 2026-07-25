package com.cmcu.itstudy.dto.community;

import jakarta.validation.constraints.Size;
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
public class CreatePostRequestDto {

    private String title;

    private String content;

    private List<String> tags;

    @Size(max = 4, message = "Maximum 4 images allowed")
    private List<String> imageUrls;

    private List<String> fileUrls;

    private CreatePollRequestDto poll;

    private Boolean allowComments;
}
