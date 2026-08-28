package site.omagotchi.learningservice.telegram.application.port;

import java.time.Duration;

public interface TelegramMessageSender {

    /** 설정된 read 타임아웃까지 기다린다. */
    void send(Long chatId, String text);

    /**
     * <b>{@code timeout} 안에 돌아온다.</b> 호출 스레드가 그보다 오래 묶이지 않는 것이 계약이다.
     *
     * <p>조치 알림이 소비처다. MQ 리스너 위에서 도는 경로라 발송 하나가 늦어져도 전체
     * 예산을 넘기면 안 된다.</p>
     *
     * <p>제한 시간이 지나면 예외로 알린다. <b>바깥으로 나간 HTTP 요청까지 되돌리지는
     * 못한다</b> — 그건 봇 클라이언트 자신의 스레드 풀에서 끝나며, 여기서 지키는 것은
     * 호출부가 기다리는 시간이다.</p>
     */
    void send(Long chatId, String text, Duration timeout);
}
