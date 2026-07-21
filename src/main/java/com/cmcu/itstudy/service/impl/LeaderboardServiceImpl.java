package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.user.LeaderboardUserDto;
import com.cmcu.itstudy.repository.DocumentRepository;
import com.cmcu.itstudy.service.contract.LeaderboardService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {

    private final DocumentRepository documentRepository;

    public LeaderboardServiceImpl(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaderboardUserDto> getLeaderboard(int size, String sortBy) {
        Pageable pageable = PageRequest.of(0, size);
        List<Object[]> rows;
        if (sortBy != null && sortBy.equalsIgnoreCase("downloads")) {
            rows = documentRepository.findLeaderboardUsersByDownloads(pageable);
        } else {
            rows = documentRepository.findLeaderboardUsersByViews(pageable);
        }
        List<LeaderboardUserDto> leaderboard = new ArrayList<>();

        int rank = 1;
        for (Object[] row : rows) {
            if (row == null || row.length < 6) {
                continue;
            }
            UUID id = null;
            if (row[0] instanceof UUID u) {
                id = u;
            } else if (row[0] instanceof String s) {
                id = UUID.fromString(s);
            } else if (row[0] != null) {
                id = UUID.fromString(row[0].toString());
            }
            String fullName = (String) row[1];
            String avatar = (String) row[2];
            long totalViews = row[3] instanceof Number n ? n.longValue() : 0L;
            long totalDownloads = row[4] instanceof Number n ? n.longValue() : 0L;
            long totalDocuments = row[5] instanceof Number n ? n.longValue() : 0L;

            leaderboard.add(LeaderboardUserDto.builder()
                    .id(id)
                    .fullName(fullName)
                    .avatar(avatar)
                    .rank(rank++)
                    .totalViews(totalViews)
                    .totalDownloads(totalDownloads)
                    .totalDocuments(totalDocuments)
                    .build());
        }

        return leaderboard;
    }
}
