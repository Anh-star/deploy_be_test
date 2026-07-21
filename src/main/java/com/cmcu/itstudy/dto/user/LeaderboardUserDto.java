package com.cmcu.itstudy.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardUserDto {
    private UUID id;
    private String fullName;
    private String avatar;
    private int rank;
    private long totalViews;
    private long totalDownloads;
    private long totalDocuments;
}
