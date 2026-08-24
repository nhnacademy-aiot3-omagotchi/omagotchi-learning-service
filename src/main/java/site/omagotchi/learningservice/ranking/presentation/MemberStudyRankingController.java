package site.omagotchi.learningservice.ranking.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.ranking.application.StudyRankingQueryService;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriodSelection;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.presentation.response.MemberStudyRankingResponse;
import site.omagotchi.learningservice.ranking.presentation.response.TodayMemberStudyRankingResponse;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/study-rankings")
public class MemberStudyRankingController {

    private final StudyRankingQueryService studyRankingQueryService;

    // 오늘 랭킹 조회 (실시간 timer_run 기록도 반영)
    @GetMapping("/today")
    public TodayMemberStudyRankingResponse getTodayRanking(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @RequestParam(required = false) Integer maxRank
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return TodayMemberStudyRankingResponse.from(
                studyRankingQueryService.getTodayMemberView(
                        user.userId(),
                        cohortId,
                        new StudyRankingQuery(maxRank)
                )
        );
    }

    // 일간 랭킹 조회 (확정된 기록만 조회 가능)
    @GetMapping("/daily/{date}")
    public MemberStudyRankingResponse getDailyRanking(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer maxRank
    ) {
        return getHistoricalRanking(
                authentication,
                cohortId,
                StudyRankingPeriodSelection.daily(date),
                maxRank
        );
    }

    // 주간 랭킹 조회
    @GetMapping("/weekly/{weekStartDate}")
    public MemberStudyRankingResponse getWeeklyRanking(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(required = false) Integer maxRank
    ) {
        return getHistoricalRanking(
                authentication,
                cohortId,
                StudyRankingPeriodSelection.weekly(weekStartDate),
                maxRank
        );
    }

    // 월간 랭킹 조회
    @GetMapping("/monthly/{month}")
    public MemberStudyRankingResponse getMonthlyRanking(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Integer maxRank
    ) {
        return getHistoricalRanking(
                authentication,
                cohortId,
                StudyRankingPeriodSelection.monthly(month),
                maxRank
        );
    }

    private MemberStudyRankingResponse getHistoricalRanking(
            JwtAuthenticationToken authentication,
            Long cohortId,
            StudyRankingPeriodSelection period,
            Integer maxRank
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return MemberStudyRankingResponse.from(
                studyRankingQueryService.getHistoricalMemberView(
                        user.userId(),
                        cohortId,
                        period,
                        new StudyRankingQuery(maxRank)
                )
        );
    }

}
