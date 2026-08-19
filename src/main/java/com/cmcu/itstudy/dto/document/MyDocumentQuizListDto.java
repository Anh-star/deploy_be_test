package com.cmcu.itstudy.dto.document;

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
public class MyDocumentQuizListDto {

    private List<MyDocumentQuizItemDto> items;
    private int page;
    private int totalPages;
    private long totalItems;
}
