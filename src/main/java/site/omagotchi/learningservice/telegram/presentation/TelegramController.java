package site.omagotchi.learningservice.telegram.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.telegram.application.TelegramUserLinkService;
import site.omagotchi.learningservice.telegram.application.dto.result.TelegramLinkTokenResponse;
import site.omagotchi.learningservice.telegram.application.dto.result.TelegramUserLinkResponse;
import site.omagotchi.learningservice.telegram.presentation.dto.request.UpdateTelegramNotificationRequest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/telegram")
public class TelegramController {

    private final TelegramUserLinkService telegramUserLinkService;

    @PostMapping("/link-token")
    public TelegramLinkTokenResponse issueLinkToken(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return telegramUserLinkService.issueLinkToken(userId);
    }

    @GetMapping("/link")
    public TelegramUserLinkResponse getMyLink(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return telegramUserLinkService.getMyLink(userId);
    }

    @PatchMapping("/link/notification")
    public TelegramUserLinkResponse updateNotification(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UpdateTelegramNotificationRequest request
    ) {
        return telegramUserLinkService.updateNotification(userId, request.toCommand());
    }

    @DeleteMapping("/link")
    public TelegramUserLinkResponse disconnect(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return telegramUserLinkService.disconnect(userId);
    }
}
