package site.omagotchi.learningservice.telegram.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.telegram.application.command.TelegramWebhookCommand;
import site.omagotchi.learningservice.telegram.application.command.UpdateTelegramNotificationCommand;
import site.omagotchi.learningservice.telegram.application.result.TelegramUserLinkResponse;
import site.omagotchi.learningservice.telegram.domain.Command;
import site.omagotchi.learningservice.telegram.domain.TelegramErrorCode;
import site.omagotchi.learningservice.telegram.infrastructure.TelegramMessageSender;

import java.util.Objects;
import java.util.Optional;

/**
 * 웹훅으로 들어온 봇 명령을 처리하고 결과를 봇으로 회신한다.
 *
 * <p><b>예외를 밖으로 내보내지 않는다.</b> 웹훅은 성공·실패 무관하게 200이어야 한다 —
 * 텔레그램은 2xx가 아니면 재시도하는데, 웹훅은 봇당 하나라 그 봇에게 오는 <b>모든</b>
 * 메시지가 여기로 들어온다. 사용자가 "안녕"이라고 보낸 것 하나가 재시도 루프를 만들면
 * 안 된다.</p>
 *
 * <p><b>상태 변경을 {@link TelegramUserLinkService}에 위임한다.</b> 여기서 엔티티를 직접
 * 고치면 트랜잭션이 없어 더티 체킹이 돌지 않고, {@code handle()}에 {@code @Transactional}을
 * 붙여도 자기호출이라 프록시를 거치지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private final TelegramUserLinkService userLinkService;
    private final CohortMembershipQueryService membershipQueryService;

    private final TelegramMessageSender messageSender;

    public void handle(TelegramWebhookCommand command) {
        Long chatId = command.telegramChatId();

        if (Objects.isNull(chatId)) {
            // 개인 채팅이 아니거나 우리가 다루지 않는 Update. 조용히 넘긴다
            return;
        }

        try {
            switch (Command.of(command.text())) {
                case START -> start(chatId, command);
                case STOP -> stop(chatId);
                case RESUME -> resume(chatId);
                case DISCONNECT -> disconnect(chatId);
                case STATUS -> status(chatId);
                case HELP, UNKNOWN -> reply(chatId, helpMessage());
            }
        } catch (BusinessException e) {
            reply(chatId, userMessageOf(e));

        } catch (RuntimeException e) {
            log.error("웹훅 처리에 실패했습니다. chatId={}", chatId, e);
            reply(chatId, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void start(Long chatId, TelegramWebhookCommand command) {
        userLinkService.linkByWebhook(command);

        reply(chatId, """
                연동이 완료되었습니다.

                앞으로 이 대화로 알림을 보내드립니다.

                /status  받고 있는 알림 보기
                /stop    알림 끄기
                /help    사용법""");
    }

    private void stop(Long chatId) {
        TelegramUserLinkResponse link = findLinkOrReply(chatId);

        if (Objects.isNull(link)) {
            return;
        }

        userLinkService.updateNotification(link.userId(), new UpdateTelegramNotificationCommand(false));

        reply(chatId, """
                알림을 껐습니다.

                연동은 그대로 유지됩니다.
                다시 받으려면 /resume 을 보내 주세요.""");
    }

    private void resume(Long chatId) {
        TelegramUserLinkResponse link = findLinkOrReply(chatId);

        if (Objects.isNull(link)) {
            return;
        }

        userLinkService.updateNotification(link.userId(), new UpdateTelegramNotificationCommand(true));
        reply(chatId, "알림을 다시 켰습니다.");
    }

    private void disconnect(Long chatId) {
        TelegramUserLinkResponse link = findLinkOrReply(chatId);

        if (Objects.isNull(link)) {
            return;
        }

        userLinkService.disconnect(link.userId());

        // 되돌리는 비용을 사후에 알린다. 실수로 눌러도 복구 경로가 보이면 치명적이지 않다
        reply(chatId, """
                연동을 해제했습니다.

                다시 받으려면 서비스 설정 화면에서 [연동하기]로
                새 링크를 받아야 합니다.

                알림만 잠시 끄고 싶었다면 다음부터는 /stop 을 쓰세요.""");
    }

    private void status(Long chatId) {
        TelegramUserLinkResponse link = findLinkOrReply(chatId);

        if (Objects.isNull(link)) {
            return;
        }

        reply(chatId, statusMessage(link));
    }

    /**
     * chat_id 로 연동을 찾는다. <b>없으면 안내를 회신하고 {@code null} 을 돌려준다.</b>
     *
     * <p>연동이 없을 때의 응답을 한 곳에 모은다 — 명령마다 같은 분기를 반복하면 문구가
     * 갈라진다. 호출부는 {@code null} 검사 후 곧바로 반환하면 된다.</p>
     */
    private TelegramUserLinkResponse findLinkOrReply(Long chatId) {
        Optional<TelegramUserLinkResponse> found = userLinkService.findByChatId(chatId);

        if (found.isEmpty()) {
            reply(chatId, notLinkedMessage());
            return null;
        }
        return found.get();
    }

    private String statusMessage(TelegramUserLinkResponse link) {
        boolean on = Boolean.TRUE.equals(link.notificationEnabled());
        boolean manager = membershipQueryService.isActiveManager(link.userId());

        return """
                연동 상태: 정상
                알림: %s

                받게 되는 알림
                · 출결 리마인더
                · 공실 알림 (신청한 회의실)%s"""
                .formatted(on ? "켜짐" : "꺼짐", manager ? "\n· 자동 조치 결과" : "");
    }

    private void reply(Long chatId, String text) {
        try {
            messageSender.send(chatId, text);
        } catch (RuntimeException e) {
            // 회신 실패가 연동·해제 결과를 뒤집지 않는다
            log.warn("웹훅 회신에 실패했습니다. chatId={}", chatId, e);
        }
    }

    private static String notLinkedMessage() {
        return """
                연동된 계정이 없습니다.

                서비스 설정 화면에서 [연동하기]를 눌러 받은 링크로 시작해 주세요.""";
    }

    private static String helpMessage() {
        return """
                오마고치 알림 봇입니다.

                /status      연동 상태와 받고 있는 알림 보기
                /stop        알림 끄기 (연동은 유지)
                /resume      알림 다시 켜기
                /disconnect  연동 해제

                연동은 서비스 설정 화면에서 [연동하기]를 눌러 시작합니다.""";
    }

    private static String userMessageOf(BusinessException e) {
        ErrorCode code = e.getErrorCode();

        if (code == TelegramErrorCode.TELEGRAM_LINK_TOKEN_EXPIRED
                || code == TelegramErrorCode.TELEGRAM_LINK_TOKEN_NOT_FOUND) {
            return "연동 링크가 만료되었거나 이미 사용되었습니다.\n서비스 설정 화면에서 다시 받아 주세요.";
        }

        if (code == TelegramErrorCode.TELEGRAM_CHAT_ALREADY_LINKED) {
            return "이 텔레그램 계정은 이미 다른 사용자와 연결되어 있습니다.";
        }

        // /start 인데 토큰이 없는 경우 등
        return "서비스 설정 화면에서 [연동하기]를 눌러 받은 링크로 시작해 주세요.";
    }
}
