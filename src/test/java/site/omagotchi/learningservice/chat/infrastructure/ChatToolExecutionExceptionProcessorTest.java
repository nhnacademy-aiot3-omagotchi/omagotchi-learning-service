package site.omagotchi.learningservice.chat.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tool 실행 실패를 LLM에게 돌려줄 문자열로 변환")
class ChatToolExecutionExceptionProcessorTest {

    private static final ToolDefinition TOOL_DEFINITION = new DefaultToolDefinition(
            "getWeather",
            "지역명으로 날씨 예보를 조회합니다.",
            "{}"
    );

    private ChatToolExecutionExceptionProcessor processor;

    @BeforeEach
    void setUp() {
        this.processor = new ChatToolExecutionExceptionProcessor();
    }

    @Test
    @DisplayName("BusinessException은 공개용 메시지만 남기고 내부 진단 정보는 버린다")
    void businessExceptionExposesOnlyPublicMessage() {
        BusinessException businessException = new BusinessException(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                "KMA API 호출 중 오류: I/O error on GET request for \"http://apis.data.go.kr/...\""
        );

        String result = this.processor.process(toolExecutionExceptionOf(businessException));

        assertThat(result).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE.message());
    }

    private static ToolExecutionException toolExecutionExceptionOf(Throwable cause) {
        return new ToolExecutionException(TOOL_DEFINITION, cause);
    }

    @Test
    @DisplayName("내부 진단 정보에 담긴 내용은 반환값에 절대 포함되지 않는다")
    void diagnosticMessageNeverLeaksIntoResult() {
        String diagnosticMessage = "KMA resultCode = 10 (최근 3일 간의 자료만 제공합니다.) serviceKey=SECRET_VALUE";
        BusinessException businessException = new BusinessException(
                CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                diagnosticMessage
        );

        String result = this.processor.process(toolExecutionExceptionOf(businessException));

        assertThat(result)
                .doesNotContain(diagnosticMessage)
                .doesNotContain("SECRET_VALUE")
                .doesNotContain("resultCode");

        // 예외 자체의 getMessage()에는 진단 정보가 들어 있다는 것도 함께 확인한다.
        // 즉 이 클래스가 없으면 저 내용이 그대로 LLM에게 전달된다.
        assertThat(businessException.getMessage()).contains(diagnosticMessage);
    }

    @Test
    @DisplayName("진단 정보가 없는 BusinessException도 공개용 메시지를 그대로 반환한다")
    void businessExceptionWithoutDiagnosticMessage() {
        BusinessException businessException = new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);

        String result = this.processor.process(toolExecutionExceptionOf(businessException));

        assertThat(result).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE.message());
    }

    @Test
    @DisplayName("예상하지 못한 예외는 원문 대신 공통 내부 오류 메시지를 반환한다")
    void unexpectedExceptionIsMasked() {
        NullPointerException cause = new NullPointerException(
                "Cannot invoke \"KmaForecastResponse$Body.items()\" because \"body\" is null"
        );

        String result = this.processor.process(toolExecutionExceptionOf(cause));

        assertThat(result)
                .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.message())
                .doesNotContain("KmaForecastResponse")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("메시지가 없는 예외도 공통 내부 오류 메시지를 반환한다")
    void exceptionWithoutMessageIsMasked() {
        String result = this.processor.process(toolExecutionExceptionOf(new IllegalStateException()));

        assertThat(result).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.message());
    }
}
