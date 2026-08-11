package com.cmcu.itstudy.dto.community;

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
public class CommunityModerationStatsDto {
    private long pendingPostsCount;
    private long pendingReportsCount;
    private long resolvedPostsCount;
    private long resolvedReportsCount;
    private long dismissedPostsCount;
    private long dismissedReportsCount;
    private long hiddenPostsCount;
}
