package site.omagotchi.learningservice.telegram.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.omagotchi.learningservice.telegram.application.TelegramUserLinkService;
import site.omagotchi.learningservice.telegram.application.dto.result.TelegramUserLinkResponse;
import site.omagotchi.learningservice.telegram.presentation.dto.request.TelegramWebhookRequest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/webhooks/telegram")
public class TelegramWebhookController {

    private final TelegramUserLinkService telegramUserLinkService;

    @PostMapping
    public TelegramUserLinkResponse receive(
            @RequestBody TelegramWebhookRequest request
    ) {
        return telegramUserLinkService.linkByWebhook(request.toCommand());
    }
}
