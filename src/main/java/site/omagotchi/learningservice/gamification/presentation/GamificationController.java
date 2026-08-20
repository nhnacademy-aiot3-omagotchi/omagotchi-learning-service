package site.omagotchi.learningservice.gamification.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.gamification.application.CharacterOnboardingService;
import site.omagotchi.learningservice.gamification.application.DailyQuestService;
import site.omagotchi.learningservice.gamification.application.GamificationProgressionService;
import site.omagotchi.learningservice.gamification.presentation.request.CreateUserCharacterRequest;
import site.omagotchi.learningservice.gamification.presentation.response.DailyQuestResponse;
import site.omagotchi.learningservice.gamification.presentation.response.GameCharacterResponse;
import site.omagotchi.learningservice.gamification.presentation.response.GamificationProgressionResponse;
import site.omagotchi.learningservice.gamification.presentation.response.HomeResponse;
import site.omagotchi.learningservice.gamification.presentation.response.UserCharacterResponse;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/gamification")
public class GamificationController {

    private final CharacterOnboardingService characterOnboardingService;
    private final DailyQuestService dailyQuestService;
    private final GamificationProgressionService gamificationProgressionService;

    @GetMapping("/characters")
    public List<GameCharacterResponse> getCharacters() {
        return characterOnboardingService.getAvailableCharacters().stream()
                .map(GameCharacterResponse::from)
                .toList();
    }

    @PostMapping("/characters/representative")
    @ResponseStatus(HttpStatus.CREATED)
    public UserCharacterResponse createRepresentativeCharacter(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateUserCharacterRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return UserCharacterResponse.from(characterOnboardingService.createRepresentativeCharacter(
                user.userId(),
                request.toCommand()
        ));
    }

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

}
