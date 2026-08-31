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
import site.omagotchi.learningservice.ranking.application.StudyRankingQueryService;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriodSelection;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.presentation.response.MemberStudyRankingResponse;
import site.omagotchi.learningservice.ranking.presentation.response.TodayMemberStudyRankingResponse;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohort-id}/teams/{team-id}/study-rankings")
public class TeamMemberStudyRankingController {

    private final StudyRankingQueryService studyRankingQueryService;

    // 특정 팀의 현재 구성원만 대상으로 실시간 개인 공부 순위를 반환한다.
    @GetMapping("/today")
    public TodayMemberStudyRankingResponse getTodayRanking(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("team-id") Long teamId,
            @RequestParam(required = false) Integer maxRank
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return TodayMemberStudyRankingResponse.from(
                studyRankingQueryService.getTodayTeamMemberView(
                        user.userId(),
                        cohortId,
                        teamId,
                        new StudyRankingQuery(maxRank)
                )
        );
    }

    // 특정 팀의 현재 구성원만 대상으로 지정한 하루의 개인 공부 순위를 반환한다.
    @GetMapping("/daily/{date}")
    public MemberStudyRankingResponse getDailyRanking(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("team-id") Long teamId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer maxRank
    ) {
        return getHistoricalRanking(
                authentication,
                cohortId,
                teamId,
                StudyRankingPeriodSelection.daily(date),
                maxRank
        );
    }

    // 특정 팀의 현재 구성원만 대상으로 요청 주간의 개인 공부 순위를 반환한다.
    @GetMapping("/weekly/{week-start-date}")
    public MemberStudyRankingResponse getWeeklyRanking(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("team-id") Long teamId,
            @PathVariable("week-start-date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStartDate,
            @RequestParam(required = false) Integer maxRank
    ) {
        return getHistoricalRanking(
                authentication,
                cohortId,
                teamId,
                StudyRankingPeriodSelection.weekly(weekStartDate),
                maxRank
        );
    }

    // 특정 팀의 현재 구성원만 대상으로 요청 월의 개인 공부 순위를 반환한다.
    @GetMapping("/monthly/{month}")
    public MemberStudyRankingResponse getMonthlyRanking(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("team-id") Long teamId,
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Integer maxRank
    ) {
        return getHistoricalRanking(
                authentication,
                cohortId,
                teamId,
                StudyRankingPeriodSelection.monthly(month),
                maxRank
        );
    }

    // 기간별 팀 내부 요청을 공통 개인 랭킹 조회와 과거 응답 변환 흐름으로 모은다.
    private MemberStudyRankingResponse getHistoricalRanking(
            JwtAuthenticationToken authentication,
            Long cohortId,
            Long teamId,
            StudyRankingPeriodSelection period,
            Integer maxRank
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return MemberStudyRankingResponse.from(
                studyRankingQueryService.getHistoricalTeamMemberView(
                        user.userId(),
                        cohortId,
                        teamId,
                        period,
                        new StudyRankingQuery(maxRank)
                )
        );
    }
}
