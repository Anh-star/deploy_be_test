package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.user.DashboardProgressPointDto;
import com.cmcu.itstudy.dto.user.UserDashboardDto;
import com.cmcu.itstudy.repository.DocumentViewRepository;
import com.cmcu.itstudy.repository.QuizAttemptRepository;
import com.cmcu.itstudy.service.contract.UserDashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class UserDashboardServiceImpl implements UserDashboardService {

    private static final List<String> FINISHED_STATUSES = List.of("PASSED", "FAILED");

    private final DocumentViewRepository documentViewRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public UserDashboardServiceImpl(
            DocumentViewRepository documentViewRepository,
            QuizAttemptRepository quizAttemptRepository
    ) {
        this.documentViewRepository = documentViewRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDashboardDto getDashboardForUser(UUID userId) {
        long totalDocumentsLearned = documentViewRepository.countDistinctDocumentsViewedByUserId(userId);
        long totalQuizzesDone = quizAttemptRepository.countFinishedExcludingOwnDocuments(userId);

        Double avgAll = quizAttemptRepository.averageSubmittedScore(userId);
        double averageScore = roundOneDecimal(avgAll != null ? avgAll : 0.0);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startThisMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startNextMonth = startThisMonth.plusMonths(1);
        LocalDateTime startLastMonth = startThisMonth.minusMonths(1);

        Double avgThisMonth = quizAttemptRepository.averageSubmittedScoreBetween(userId, startThisMonth, startNextMonth);
        Double avgLastMonth = quizAttemptRepository.averageSubmittedScoreBetween(userId, startLastMonth, startThisMonth);
        double progressPercent = roundOneDecimal(monthOverMonthPercent(avgThisMonth, avgLastMonth));

        LocalDateTime historySince = now.minusDays(365);
        List<DashboardProgressPointDto> progressHistory = mapDailyAverages(
                quizAttemptRepository.findDailyAverageScoresSince(userId, historySince)
        );

        return UserDashboardDto.builder()
                .totalDocumentsLearned(totalDocumentsLearned)
                .totalQuizzesDone(totalQuizzesDone)
                .averageScore(averageScore)
                .progressPercent(progressPercent)
                .progressHistory(progressHistory)
                .build();
    }

    private static double monthOverMonthPercent(Double currentAvg, Double previousAvg) {
        double c = currentAvg != null ? currentAvg : 0.0;
        double p = previousAvg != null ? previousAvg : 0.0;
        if (p <= 1e-6) {
            return c > 1e-6 ? 100.0 : 0.0;
        }
        return ((c - p) / p) * 100.0;
    }

    private static double roundOneDecimal(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static List<DashboardProgressPointDto> mapDailyAverages(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<DashboardProgressPointDto> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            LocalDate day = toLocalDate(row[0]);
            if (day == null) {
                continue;
            }
            double score = roundOneDecimal(toDouble(row[1]));
            out.add(DashboardProgressPointDto.builder()
                    .date(day.toString())
                    .score(score)
                    .build());
        }
        return out;
    }

    private static LocalDate toLocalDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDate ld) {
            return ld;
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (o instanceof java.util.Date d) {
            return new java.sql.Date(d.getTime()).toLocalDate();
        }
        if (o instanceof java.time.Instant ins) {
            return ins.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private static double toDouble(Object o) {
        if (o == null) {
            return 0.0;
        }
        if (o instanceof Double d) {
            return d;
        }
        if (o instanceof Float f) {
            return f.doubleValue();
        }
        if (o instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }
}
