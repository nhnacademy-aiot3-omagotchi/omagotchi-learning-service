package site.omagotchi.learningservice.statistics.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.statistics.application.MemberStatisticsService;
import site.omagotchi.learningservice.statistics.application.CohortStatisticsService;
import site.omagotchi.learningservice.statistics.presentation.response.MemberDailyRecordsResponse;
import site.omagotchi.learningservice.statistics.presentation.response.MemberOverviewResponse;
import site.omagotchi.learningservice.statistics.presentation.response.MemberPageResponse;
import site.omagotchi.learningservice.statistics.presentation.response.TodayResponse;
import site.omagotchi.learningservice.statistics.presentation.response.TrendResponse;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohort-id}/study-statistics")
public class StudyStatisticsController {

    private final CohortStatisticsService cohortStatisticsService;
    private final MemberStatisticsService memberStatisticsService;

    @GetMapping("/today")
    public ResponseEntity<TodayResponse> getToday(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return ResponseEntity.ok(TodayResponse.from(
                cohortStatisticsService.getToday(user.userId(), cohortId)
        ));
    }

    @GetMapping("/trend")
    public ResponseEntity<TrendResponse> getTrend(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @RequestParam String window
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return ResponseEntity.ok(TrendResponse.from(
                cohortStatisticsService.getTrend(
                        user.userId(),
                        cohortId,
                        window
                )
        ));
    }

    @GetMapping("/members")
    public ResponseEntity<MemberPageResponse> getMembers(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @RequestParam String window,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return ResponseEntity.ok(MemberPageResponse.from(
                memberStatisticsService.getMembers(
                        user.userId(),
                        cohortId,
                        window,
                        page,
                        size,
                        sort
                )
        ));
    }

    @GetMapping("/members/{cohort-membership-id}/overview")
    public ResponseEntity<MemberOverviewResponse> getMemberOverview(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("cohort-membership-id") Long cohortMembershipId,
            @RequestParam String window
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return ResponseEntity.ok(MemberOverviewResponse.from(
                memberStatisticsService.getOverview(
                        user.userId(),
                        cohortId,
                        cohortMembershipId,
                        window
                )
        ));
    }

    @GetMapping("/members/{cohort-membership-id}/records")
    public ResponseEntity<MemberDailyRecordsResponse> getMemberDailyRecords(
            JwtAuthenticationToken authentication,
            @PathVariable("cohort-id") Long cohortId,
            @PathVariable("cohort-membership-id") Long cohortMembershipId,
            @RequestParam LocalDate date
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);

        return ResponseEntity.ok(MemberDailyRecordsResponse.from(
                memberStatisticsService.getDailyRecords(
                        user.userId(),
                        cohortId,
                        cohortMembershipId,
                        date
                )
        ));
    }
}
