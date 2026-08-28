package site.omagotchi.learningservice.telegram.presentation.response;

import site.omagotchi.learningservice.telegram.application.result.TelegramLinkTokenResult;

import java.time.OffsetDateTime;

/**
 * 연동 딥링크 발급 응답.
 *
 * <p>application의 {@link TelegramLinkTokenResult}와 필드가 같아도 <b>따로 둔다.</b>
 * Result를 그대로 반환하면 내부 출력 형태를 바꾸는 순간 외부 API 계약이 함께 깨진다.</p>
 */
public record TelegramLinkTokenResponse(
        String linkUrl,
        OffsetDateTime expiresAt
) {

    public static TelegramLinkTokenResponse from(TelegramLinkTokenResult result) {
        return new TelegramLinkTokenResponse(result.linkUrl(), result.expiresAt());
    }
}
