package site.omagotchi.learningservice.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String REQUEST_URI = "/test";
    private static final String DIAGNOSTIC_MESSAGE =
            "operation = update, expectedStatus = ACTIVE, actualStatus = CLOSED";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("호출 계약 위반을 내부 오류로 숨김")
    void hidesIllegalArgumentException() {
        // Given
        MockHttpServletRequest request = requestForTest();
        IllegalArgumentException exception =
                new IllegalArgumentException("외부에 노출하면 안 되는 인자 정보");

        // When
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpectedException(exception, request);

        // Then
        thenUnexpectedExceptionIsHidden(response);
    }

    @Test
    @DisplayName("내부 상태 위반을 내부 오류로 숨김")
    void hidesIllegalStateException() {
        // Given
        MockHttpServletRequest request = requestForTest();
        IllegalStateException exception =
                new IllegalStateException("외부에 노출하면 안 되는 상태 정보");

        // When
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpectedException(exception, request);

        // Then
        thenUnexpectedExceptionIsHidden(response);
    }

    @Test
    @DisplayName("외부 응답에서 진단 메시지 숨김")
    void hidesDiagnosticMessageFromResponse() {
        // Given
        MockHttpServletRequest request = requestForTest();
        BusinessException exception = new BusinessException(
                CommonErrorCode.INVALID_REQUEST,
                DIAGNOSTIC_MESSAGE
        );

        // When
        ResponseEntity<ApiErrorResponse> response =
                handler.handleBusinessException(exception, request);

        // Then
        then(response.getBody().message())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST.message());
    }

    @Test
    @DisplayName("호출 대상 서비스 장애 기록")
    void logsServerSideBusinessFailure(CapturedOutput output) {
        // Given: 원본 예외를 포함한 호출 대상 서비스 장애
        MockHttpServletRequest request = requestForTest();
        IllegalStateException cause =
                new IllegalStateException("test service connection failure");

        // When: 공개 ErrorCode가 확정된 5xx 오류의 공통 응답 변환
        ResponseEntity<ApiErrorResponse> response = handler.handleBusinessException(
                new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, cause),
                request
        );

        // Then: 공개 상태와 원본 예외 기록
        assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            softly.assertThat(output)
                    .contains("code=COMMON_SERVICE_UNAVAILABLE")
                    .contains("test service connection failure");
        });
    }

    private MockHttpServletRequest requestForTest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(REQUEST_URI);
        return request;
    }

    private void thenUnexpectedExceptionIsHidden(ResponseEntity<ApiErrorResponse> response) {
        then(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        then(response.getBody()).isEqualTo(new ApiErrorResponse(
                CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
                CommonErrorCode.INTERNAL_SERVER_ERROR.message(),
                REQUEST_URI,
                null
        ));
    }
}
