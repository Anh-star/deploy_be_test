package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.user.LeaderboardUserDto;
import com.cmcu.itstudy.service.contract.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaderboardUserDto>>> getLeaderboard(
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sortBy", defaultValue = "views") String sortBy
    ) {
        if (size > 50) {
            size = 50;
        }
        List<LeaderboardUserDto> data = leaderboardService.getLeaderboard(size, sortBy);
        return ResponseEntity.ok(ApiResponse.success(data, "Leaderboard retrieved successfully"));
    }
}
