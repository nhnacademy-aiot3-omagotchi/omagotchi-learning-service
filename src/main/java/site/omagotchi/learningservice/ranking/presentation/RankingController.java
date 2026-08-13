package site.omagotchi.learningservice.ranking.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.ranking.application.RankingQueryService;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.presentation.response.StudyRankingResponse;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private final RankingQueryService rankingQueryService;

    @GetMapping("/study")
    public StudyRankingResponse getStudyRanking(
            @RequestParam Long cohortId,
            @RequestParam RankingPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return StudyRankingResponse.from(rankingQueryService.getStudyRanking(
                user.userId(),
                cohortId,
                period,
                baseDate
        ));
    }
}
