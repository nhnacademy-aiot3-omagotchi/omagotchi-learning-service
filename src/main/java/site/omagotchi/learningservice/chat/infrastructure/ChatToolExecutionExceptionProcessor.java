package site.omagotchi.learningservice.chat.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

/**
 * Tool 실행이 실패했을 때 LLM에게 돌려줄 문자열에서 내부 정보를 걷어낸다
 * Spring AI는 Tool에서 던진 예외를 잡아 getMessage()를 Tool 실행 결과로 LLM에게 그대로 전달한다
 * BusinessException의 메시지에는 공개용 errorCode.message() 뒤에 내부 진단 정보(diagnosticMessage)가 붙어 있어,
 * 기본 동작에 맡기면 밑 레벨 서비스의 오류 원문이 LLM을 거쳐 사용자에게까지 노출된다
 * REST 경로는 예외 핸들러가 errorCode만 응답에 싣고 진단 정보는 로그로만 보낸다
 * Tool 경로에도 같은 경계를 세워 두 경로가 같은 규칙을 따르게 한다
 */
@Slf4j
@Component
public class ChatToolExecutionExceptionProcessor implements ToolExecutionExceptionProcessor {

    @Override
    public String process(ToolExecutionException exception) {
        String toolName = exception.getToolDefinition().name();
        Throwable cause = exception.getCause();

        if (cause instanceof BusinessException businessException) {
            log.warn("[ChatToolExecutionExceptionProcessor] Tool 실행 실패 - Tool = {}", toolName, exception);
            return businessException.getErrorCode().message();
        }

        // 예상치 못한 실패는 원문을 그대로 흘리지 않는다
        log.error("[ChatToolExecutionExceptionProcessor] Tool 실행 중 예상치 못한 오류 - Tool = {}", toolName, exception);
        return CommonErrorCode.INTERNAL_SERVER_ERROR.message();
    }
}
