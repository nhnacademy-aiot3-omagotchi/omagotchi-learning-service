package site.omagotchi.learningservice.telegram.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/***
 *  텔레그램 연동과 관련된 설정값들
 *
 * @param bot 텔레그램 봇 연동 설정 값 (봇 정보, 타임아웃)
 * @param linkToken 딥링크 설정 값 (딥링크 ttl)
 * @param webhook 웹훅 설정 값 (시크릿 토큰)
 */
@Validated
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        @Valid
        @NotNull(message = "telegram.bot은 필수입니다.")
        Bot bot,

        @Valid
        @NotNull(message = "telegram.link-token은 필수입니다.")
        LinkToken linkToken,

        @Valid
        @NotNull(message = "telegram.webhook은 필수입니다.")
        Webhook webhook
) {
    /**
     * 텔레그램 봇 설정
     *
     * @param username 딥 링크에 들어가는 이
     * @param token 봇 토큰
     * @param maxThreads 내부 풀 크기
     * @param timeout 요청 타임아웃
     */
    public record Bot(
            @NotBlank(message = "telegram.bot.username은 비어 있을 수 없습니다.")
            String username,

            @NotBlank(message = "TELEGRAM_BOT_TOKEN은 비어 있을 수 없습니다.")
            String token,

            int maxThreads,

            @Valid
            Timeout timeout
    ) {
        private static final int DEFAULT_MAX_THREADS = 4;

        /**
         * 타임아웃 블록을 생략해도 기동되게 한다. {@code Timeout} 내부가 값별로 기본값을
         * 채우는 것과 같은 방식이며, 블록 자체가 없을 때를 여기서 받는다.
         *
         * <p>{@code username}·{@code token}과 달리 타임아웃은 합리적인 기본값이 있어
         * 환경마다 정하라고 요구할 이유가 없다. 필수와 선택을 가르는 선이 여기다.</p>
         */
        public Bot {
            if (Objects.isNull(timeout)) {
                timeout = new Timeout(null, null, null);
            }
            if (maxThreads <= 0) {
                maxThreads = DEFAULT_MAX_THREADS;
            }
        }
        private static final int CONNECTION_REQUEST_DURATION  = 5;
        private static final int CONNECTION_DURATION = 5;
        private static final int READ_DURATION = 10;

        /**
         * 전체적인 타임아웃
         *
         * @param connectionRequest 커넥션 풀 요청 타임아웃
         * @param connect TCP 연결 타임아웃
         * @param read 연결뒤 무응답 상환
         */
        public record Timeout(
                Duration connectionRequest,
                Duration connect,
                Duration read
        ){

            //컴팩트 생성자 검증
            public Timeout{
                if(Objects.isNull(connectionRequest) || connectionRequest.isNegative() || connectionRequest.isZero()){
                    connectionRequest = Duration.ofSeconds(CONNECTION_REQUEST_DURATION);
                }

                if (Objects.isNull(connect) || connect.isNegative() || connect.isZero()){
                    connect = Duration.ofSeconds(CONNECTION_DURATION);
                }

                if(Objects.isNull(read) || read.isNegative() || read.isZero()){
                    read = Duration.ofSeconds(READ_DURATION);
                }
            }
        }
    }

    /**
     * 딥링크 설정
     *
     * @param ttl 딥링크 유효 시간
     */
    public record LinkToken(
            @NotNull(message = "telegram.link-token.ttl은 필수입니다.")
            @DurationMin(seconds = 1, message = "telegram.link-token.ttl은 1초 이상이어야 합니다.")
            Duration ttl
    ) { }

    /**
     * 웹훅 설정
     *
     * @param secret 시크릿 토큰 (우리가 생성)
     */
    public record Webhook(
            @NotBlank(message = "TELEGRAM_WEBHOOK_SECRET은 비어 있을 수 없습니다.")
            String secret
    ) { }

}
