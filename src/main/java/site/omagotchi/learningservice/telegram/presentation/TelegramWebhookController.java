package site.omagotchi.learningservice.telegram.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.omagotchi.learningservice.telegram.application.TelegramWebhookService;
import site.omagotchi.learningservice.telegram.presentation.request.TelegramWebhookRequest;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/webhooks/telegram")
public class TelegramWebhookController {
    /** 텔레그램이 setWebhook 의 secret_token 을 실어 보내는 헤더. 이름이 하나라도 다르면 전부 401이 된다 */
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramWebhookService webhookService;
    private final TelegramWebhookAuthenticator authenticator;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(name = SECRET_HEADER, required = false) String secret,
            @RequestBody TelegramWebhookRequest request
    ) {
        if (!authenticator.isTelegram(secret)) {
            log.warn("텔레그램 웹훅 시크릿이 일치하지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        webhookService.handle(request.toCommand());

        return ResponseEntity.ok().build();
    }
}
