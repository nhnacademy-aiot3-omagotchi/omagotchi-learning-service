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
import site.omagotchi.learningservice.ranking.presentation.response.StudyRankingBoardResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cohorts/{cohortId}/study-rankings/management")
public class ManagerStudyRankingController {

    private final StudyRankingQueryService studyRankingQueryService;

    @GetMapping
    public StudyRankingBoardResponse getRanking(
            JwtAuthenticationToken authentication,
            @PathVariable Long cohortId,
            @RequestParam StudyRankingPeriod period,
            @RequestParam(required = false) Integer maxRank
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return StudyRankingBoardResponse.from(
                studyRankingQueryService.getManagerBoard(
                        user.userId(),
                        cohortId,
                        new StudyRankingQuery(period, maxRank)
                )
        );
    }
}
