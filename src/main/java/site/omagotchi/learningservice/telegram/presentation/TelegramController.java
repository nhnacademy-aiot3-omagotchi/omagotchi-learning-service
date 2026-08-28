package site.omagotchi.learningservice.telegram.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.telegram.application.TelegramUserLinkService;
import site.omagotchi.learningservice.telegram.presentation.response.TelegramLinkTokenResponse;
import site.omagotchi.learningservice.telegram.presentation.response.TelegramUserLinkResponse;
import site.omagotchi.learningservice.telegram.presentation.request.UpdateTelegramNotificationRequest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/telegram")
public class TelegramController {

    private final TelegramUserLinkService telegramUserLinkService;

    @PostMapping("/link-token")
    public TelegramLinkTokenResponse issueLinkToken(
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return TelegramLinkTokenResponse.from(telegramUserLinkService.issueLinkToken(user.userId()));
    }

    @GetMapping("/link")
    public TelegramUserLinkResponse getMyLink(
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return TelegramUserLinkResponse.from(telegramUserLinkService.getMyLink(user.userId()));
    }

    @PatchMapping("/link/notification")
    public TelegramUserLinkResponse updateNotification(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody UpdateTelegramNotificationRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return TelegramUserLinkResponse.from(
                telegramUserLinkService.updateNotification(user.userId(), request.toCommand()));
    }

    @DeleteMapping("/link")
    public TelegramUserLinkResponse disconnect(
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return TelegramUserLinkResponse.from(telegramUserLinkService.disconnect(user.userId()));
    }
}
