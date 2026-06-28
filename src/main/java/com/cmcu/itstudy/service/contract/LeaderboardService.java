package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.user.LeaderboardUserDto;
import java.util.List;

public interface LeaderboardService {
    List<LeaderboardUserDto> getLeaderboard(int size, String sortBy);
}
