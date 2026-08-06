package site.omagotchi.learningservice.gamification.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.gamification.application.DailyQuestService;
import site.omagotchi.learningservice.gamification.application.GamificationProgressionService;
import site.omagotchi.learningservice.gamification.presentation.response.DailyQuestResponse;
import site.omagotchi.learningservice.gamification.presentation.response.GamificationProgressionResponse;
import site.omagotchi.learningservice.gamification.presentation.response.HomeResponse;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/gamification")
public class GamificationController {

    private final DailyQuestService dailyQuestService;
    private final GamificationProgressionService gamificationProgressionService;

    @GetMapping("/home")
    public HomeResponse getHome(JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return HomeResponse.from(dailyQuestService.getHome(user.userId()));
    }

    @GetMapping("/quests/daily")
    public List<DailyQuestResponse> getDailyQuests(JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return dailyQuestService.getOrCreateDailyQuests(user.userId()).stream()
                .map(DailyQuestResponse::from)
                .toList();
    }

    @GetMapping("/progression")
    public GamificationProgressionResponse getProgression(
            @RequestParam Long cohortId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate aggregationDate,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return GamificationProgressionResponse.from(gamificationProgressionService.getProgression(
                user.userId(),
                cohortId,
                aggregationDate
        ));
    }

    @PostMapping("/quests/{userDailyQuestId}/claim")
    public DailyQuestResponse claim(
            @PathVariable Long userDailyQuestId,
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return DailyQuestResponse.from(dailyQuestService.claim(user.userId(), userDailyQuestId));
    }

    @PostMapping("/events/attendance")
    public DailyQuestResponse handleAttendance(JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return DailyQuestResponse.from(dailyQuestService.handleAttendance(user.userId()));
    }

    @PostMapping("/events/study-completed")
    public DailyQuestResponse handleStudyCompleted(JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return DailyQuestResponse.from(dailyQuestService.handleStudyCompleted(user.userId()));
    }

    @PostMapping("/events/character-checked")
    public DailyQuestResponse handleCharacterChecked(JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return DailyQuestResponse.from(dailyQuestService.handleCharacterChecked(user.userId()));
    }

    @PostMapping("/events/llm-quest-completed")
    public DailyQuestResponse handleLlmQuestCompleted(JwtAuthenticationToken authentication) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return DailyQuestResponse.from(dailyQuestService.handleLlmQuestCompleted(user.userId()));
    }
}
