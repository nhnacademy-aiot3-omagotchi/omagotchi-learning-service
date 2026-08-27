package site.omagotchi.learningservice.telegram.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

/**
 * Telegram 사용자 연동 링크 발급 정책이다.
 */
@Validated
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        @Valid
        @NotNull(message = "telegram.bot은 필수입니다.")
        Bot bot,

        @Valid
        @NotNull(message = "telegram.link-token은 필수입니다.")
        LinkToken linkToken
) {
    /**
     * 텔레그램 봇 설정
     *
     * @param enabled 봇 활성화 여부
     * @param username 딥 링크에 들어가는 이
     * @param token 봇 토큰
     * @param maxThreads 내부 풀 크기
     * @param timeout 요청 타임아웃
     */
    public record Bot(
            boolean enabled,

            @NotBlank(message = "telegram.bot.username은 비어 있을 수 없습니다.")
            String username,

            String token,

            int maxThreads,

            @Valid
            Timeout timeout
    ) {
        private static final int DEFAULT_MAX_THREADS = 4;

        /**
         * 설정을 생략해도 기동되게 한다. {@code Timeout} 내부가 값별로 기본값을 채우는 것과
         * 같은 방식이며, 블록 자체가 없을 때를 여기서 받는다 — 발송을 끄고 쓰는 환경에서
         * 타임아웃 설정을 강제하지 않기 위해서다.
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

    public record LinkToken(
            @NotNull(message = "telegram.link-token.ttl은 필수입니다.")
            @DurationMin(seconds = 1, message = "telegram.link-token.ttl은 1초 이상이어야 합니다.")
            Duration ttl
    ) { }

}
