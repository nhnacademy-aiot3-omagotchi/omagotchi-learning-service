package site.omagotchi.learningservice.telegram.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자가 보낸 원문에서 명령을 골라내는 규칙을 고정한다.
 *
 * <p>웹훅은 봇에게 오는 <b>모든</b> 메시지를 받는다. 명령이 아닌 잡담이 훨씬 많으므로
 * "무엇이 명령인가"보다 <b>"무엇이 명령이 아닌가"</b>가 더 중요하다 — 잘못 잡으면
 * 인사말 하나가 연동을 끊는다.</p>
 */
class CommandTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @DisplayName("명령어를 그대로 알아본다.")
    @CsvSource({
            "/start,     START",
            "/stop,      STOP",
            "/resume,    RESUME",
            "/disconnect,DISCONNECT",
            "/status,    STATUS",
            "/help,      HELP"
    })
    void recognizesEachCommand(String text, Command expected) {
        assertThat(Command.of(text)).isEqualTo(expected);
    }

    /**
     * 딥링크로 들어오면 {@code /start <token>} 형태다. 뒤에 붙는 것은 명령 판별에
     * 관여하지 않는다 — 토큰 해석은 연동 Service의 몫이다.
     */
    @Test
    @DisplayName("뒤에 붙은 인자를 무시하고 앞머리만 본다.")
    void ignoresArguments() {
        assertThat(Command.of("/start a1b2c3d4")).isEqualTo(Command.START);
    }

    /**
     * 그룹에서는 텔레그램 클라이언트가 {@code /status@봇이름} 형태로 보낸다. 개인 대화만
     * 받도록 걸러내지만, 파싱 자체는 이 형태를 알아야 한다.
     */
    @Test
    @DisplayName("@봇이름 접미사를 떼고 본다.")
    void stripsBotMention() {
        assertThat(Command.of("/status@omagotchi_bot")).isEqualTo(Command.STATUS);
    }

    @Test
    @DisplayName("앞뒤 공백을 무시한다.")
    void trimsSurroundingWhitespace() {
        assertThat(Command.of("   /stop   ")).isEqualTo(Command.STOP);
    }

    /**
     * <b>접두사 일치가 아니라 완전 일치다.</b> 접두사로 잡으면 {@code /stopwatch} 같은
     * 입력이 알림을 꺼 버린다.
     */
    @Test
    @DisplayName("명령어로 시작하기만 하는 입력은 명령이 아니다.")
    void doesNotMatchByPrefix() {
        assertThat(Command.of("/stopwatch")).isEqualTo(Command.UNKNOWN);
    }

    /**
     * 대문자는 알아보지 못한다. 텔레그램 명령은 소문자가 규약이고 클라이언트가 그렇게
     * 자동완성하므로 정규화하지 않는다 — 잘못 들어와도 도움말로 떨어져 안전하다.
     */
    @ParameterizedTest
    @DisplayName("명령이 아닌 입력은 모두 UNKNOWN이다.")
    @ValueSource(strings = {"안녕하세요", "stop", "//stop", "/STOP", "stop /stop", "?", "/"})
    void treatsEverythingElseAsUnknown(String text) {
        assertThat(Command.of(text)).isEqualTo(Command.UNKNOWN);
    }

    @ParameterizedTest
    @DisplayName("빈 입력도 UNKNOWN이다.")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n"})
    void treatsBlankAsUnknown(String text) {
        assertThat(Command.of(text)).isEqualTo(Command.UNKNOWN);
    }
}
