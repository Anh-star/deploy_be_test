package com.cmcu.itstudy.dto.admin.user;

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
public class AdminUserPageResponseDto {

    private List<AdminUserResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private long activeCount;
    private long lockedCount;
}
