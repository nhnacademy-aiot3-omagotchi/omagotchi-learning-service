package site.omagotchi.learningservice.ranking.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.ranking.application.TeamStudyRankingQueryService;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriodSelection;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.presentation.response.TeamStudyRankingResponse;
import site.omagotchi.learningservice.ranking.presentation.response.TodayTeamStudyRankingResponse;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/study-rankings/teams")
public class TeamStudyRankingController {

    private final TeamStudyRankingQueryService teamStudyRankingQueryService;

    // 현재 집계일의 실시간 공부시간을 반영한 팀별 순위를 반환한다.
    @GetMapping("/today")
    public TodayTeamStudyRankingResponse getTodayRanking(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @RequestParam(required = false) Integer maxRank
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return TodayTeamStudyRankingResponse.from(
                teamStudyRankingQueryService.getTodayTeamView(
                        user.userId(),
                        cohortId,
                        new StudyRankingQuery(maxRank)
                )
        );
    }

    // 지정한 종료 집계일 하루의 확정 공부시간으로 팀별 순위를 반환한다.
    @GetMapping("/daily/{date}")
    public TeamStudyRankingResponse getDailyRanking(
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

    // 월요일부터 시작하는 요청 주간의 종료된 집계일까지 팀별 순위를 반환한다.
    @GetMapping("/weekly/{weekStartDate}")
    public TeamStudyRankingResponse getWeeklyRanking(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStartDate,
            @RequestParam(required = false) Integer maxRank
    ) {
        return getHistoricalRanking(
                authentication,
                cohortId,
                StudyRankingPeriodSelection.weekly(weekStartDate),
                maxRank
        );
    }

    // 요청 월의 종료된 집계일까지 확정 공부시간으로 팀별 순위를 반환한다.
    @GetMapping("/monthly/{month}")
    public TeamStudyRankingResponse getMonthlyRanking(
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

    // 기간별 요청을 공통 Application 조회와 과거 응답 변환 흐름으로 모은다.
    private TeamStudyRankingResponse getHistoricalRanking(
            JwtAuthenticationToken authentication,
            Long cohortId,
            StudyRankingPeriodSelection period,
            Integer maxRank
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return TeamStudyRankingResponse.from(
                teamStudyRankingQueryService.getHistoricalTeamView(
                        user.userId(),
                        cohortId,
                        period,
                        new StudyRankingQuery(maxRank)
                )
        );
    }
}
