package site.omagotchi.learningservice.telegram.presentation.response;

import site.omagotchi.learningservice.telegram.application.result.TelegramUserLinkResult;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 연동 상태 응답.
 *
 * <p>application의 {@link TelegramUserLinkResult}와 나눠 둔 이유는 {@code TelegramLinkTokenResponse}
 * 와 같다 — 내부 출력 형태와 외부 계약이 함께 움직이면 안 된다.</p>
 *
 * <p>{@code telegramUserId}·{@code telegramChatId}를 그대로 내보낸다. 프론트가 연동 여부를
 * 판정하는 데 쓰지는 않지만(그 판정은 200/404가 한다) 디버깅에 필요한 값이라 남긴다.</p>
 */
public record TelegramUserLinkResponse(
        UUID userId,
        Long telegramUserId,
        Long telegramChatId,
        Boolean notificationEnabled,
        OffsetDateTime linkedAt,
        OffsetDateTime disconnectedAt
) {

    public static TelegramUserLinkResponse from(TelegramUserLinkResult result) {
        return new TelegramUserLinkResponse(
                result.userId(),
                result.telegramUserId(),
                result.telegramChatId(),
                result.notificationEnabled(),
                result.linkedAt(),
                result.disconnectedAt()
        );
    }
}
