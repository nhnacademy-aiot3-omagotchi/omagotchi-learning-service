package site.omagotchi.learningservice.telegram.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/telegram-links")
public class TelegramController {

    private final TelegramUserLinkService telegramUserLinkService;

    @PostMapping("/link-tokens")
    public TelegramLinkTokenResponse issueLinkToken(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return telegramUserLinkService.issueLinkToken(userId);
    }

    @GetMapping("/me")
    public TelegramUserLinkResponse getMyLink(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return telegramUserLinkService.getMyLink(userId);
    }

    @PatchMapping("/notifications")
    public ResponseEntity<Void> updateNotification(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateTelegramNotificationRequest request
    ) {
        telegramUserLinkService.updateNotification(userId, request.toCommand());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/disconnect")
    public TelegramUserLinkResponse disconnect(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return telegramUserLinkService.disconnect(userId);
    }
}
