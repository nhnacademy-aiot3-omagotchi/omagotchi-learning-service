package site.omagotchi.learningservice.ranking.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.ranking.application.StudyRankingQueryService;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriod;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.presentation.response.MemberStudyRankingResponse;
import site.omagotchi.learningservice.ranking.presentation.response.MyStudyRankingResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/study-rankings")
public class MemberStudyRankingController {

    private final StudyRankingQueryService studyRankingQueryService;

    @GetMapping
    public MemberStudyRankingResponse getRanking(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @RequestParam StudyRankingPeriod period,
            @RequestParam(required = false) Integer maxRank
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return MemberStudyRankingResponse.from(
                studyRankingQueryService.getMemberView(
                        user.userId(),
                        cohortId,
                        new StudyRankingQuery(period, maxRank)
                )
        );
    }

    @GetMapping("/me")
    public MyStudyRankingResponse getMyRanking(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @RequestParam StudyRankingPeriod period
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return MyStudyRankingResponse.from(
                studyRankingQueryService.getMine(
                        user.userId(),
                        cohortId,
                        period
                )
        );
    }
}
