package site.omagotchi.learningservice.telegram.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.global.auth.AuthenticatedUser;
import site.omagotchi.learningservice.telegram.application.TelegramUserLinkService;
import site.omagotchi.learningservice.telegram.application.result.TelegramLinkTokenResponse;
import site.omagotchi.learningservice.telegram.application.result.TelegramUserLinkResponse;
import site.omagotchi.learningservice.telegram.presentation.dto.request.UpdateTelegramNotificationRequest;

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
        return telegramUserLinkService.issueLinkToken(user.userId());
    }

    @GetMapping("/link")
    public TelegramUserLinkResponse getMyLink(
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return telegramUserLinkService.getMyLink(user.userId());
    }

    @PatchMapping("/link/notification")
    public TelegramUserLinkResponse updateNotification(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody UpdateTelegramNotificationRequest request
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return telegramUserLinkService.updateNotification(user.userId(), request.toCommand());
    }

    @DeleteMapping("/link")
    public TelegramUserLinkResponse disconnect(
            JwtAuthenticationToken authentication
    ) {
        AuthenticatedUser user = AuthenticatedUser.from(authentication);
        return telegramUserLinkService.disconnect(user.userId());
    }
}
