package site.omagotchi.learningservice.global.http;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class RestClientCallExecutorTest {

    private final RestClientCallExecutor executor = new RestClientCallExecutor();

    @Test
    @DisplayName("HTTP 연결 실패의 503 변환")
    void mapsConnectionFailureToServiceUnavailable() {
        // Given: HTTP 응답 없는 연결 실패
        ResourceAccessException failure = new ResourceAccessException("connection failure");

        // When: 공통 HTTP 호출 실행
        ThrowingCallable action = () -> executor.execute(
                () -> {
                    throw failure;
                },
                exception -> {
                    throw new BusinessException(
                            CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                            exception
                    );
                }
        );

        // Then: 서비스 일시 장애 변환과 원인 보존
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertSoftly(softly -> {
                        softly.assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
                        softly.assertThat(exception.getCause()).isSameAs(failure);
                    });
                });
    }

    @Test
    @DisplayName("호출 대상 5xx의 503 변환")
    void mapsServerErrorToServiceUnavailable() {
        // Given: 호출 대상 서비스의 5xx 응답
        RestClientResponseException failure = new RestClientResponseException(
                "Service Unavailable",
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                new HttpHeaders(),
                new byte[0],
                StandardCharsets.UTF_8
        );

        // When: 공통 HTTP 호출 실행
        ThrowingCallable action = () -> executor.execute(
                () -> {
                    throw failure;
                },
                exception -> {
                    throw new BusinessException(
                            CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                            exception
                    );
                }
        );

        // Then: 서비스 일시 장애 변환과 원인 보존
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertSoftly(softly -> {
                        softly.assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
                        softly.assertThat(exception.getCause()).isSameAs(failure);
                    });
                });
    }

    @Test
    @DisplayName("4xx 응답의 호출별 처리 결과 반환")
    void returnsCallSpecificClientErrorResult() {
        // Given: 호출별 결과 판단이 필요한 4xx 응답
        RestClientResponseException failure = new RestClientResponseException(
                "Bad Request",
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                new HttpHeaders(),
                new byte[0],
                StandardCharsets.UTF_8
        );

        // When: 4xx 처리 정책을 포함한 공통 HTTP 호출 실행
        String result = executor.execute(
                () -> {
                    throw failure;
                },
                exception -> {
                    assertThat(exception).isSameAs(failure);
                    return "recovered-result";
                }
        );

        // Then: 호출별 정책이 만든 결과 반환
        assertThat(result).isEqualTo("recovered-result");
    }

    @Test
    @DisplayName("무관한 IllegalStateException의 원본 전파")
    void rethrowsUnrelatedIllegalStateException() {
        // Given: LoadBalancer 인스턴스 부재와 무관한 상태 오류
        IllegalStateException failure = new IllegalStateException(
                "No instances available for misleading-message"
        );

        // When: 공통 HTTP 호출 실행
        ThrowingCallable action = () -> executor.execute(
                () -> {
                    throw failure;
                },
                exception -> {
                    throw new BusinessException(
                            CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                            exception
                    );
                }
        );

        // Then: 원본 상태 오류 전파
        assertThatThrownBy(action).isSameAs(failure);
    }

    @Test
    @DisplayName("분류하지 않은 RestClientException의 원본 전파")
    void rethrowsUnclassifiedRestClientException() {
        // Given: 응답 본문 해석 실패로 분류할 수 없는 RestClient 오류
        RestClientException failure = new RestClientException("unclassified failure");

        // When: 공통 HTTP 호출 실행
        ThrowingCallable action = () -> executor.execute(
                () -> {
                    throw failure;
                },
                exception -> {
                    throw new BusinessException(
                            CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                            exception
                    );
                }
        );

        // Then: 원본 RestClient 오류 전파
        assertThatThrownBy(action).isSameAs(failure);
    }

    @Test
    @DisplayName("성공 응답 Content-Type 계약 위반의 502 변환")
    void mapsUnknownResponseContentTypeToBadGateway() {
        // Given: 2xx 응답이지만 선언한 응답 Body로 읽을 수 없는 Content-Type
        UnknownContentTypeException failure = new UnknownContentTypeException(
                String.class,
                MediaType.TEXT_HTML,
                HttpStatus.OK,
                "OK",
                new HttpHeaders(),
                "<html>unexpected</html>".getBytes(StandardCharsets.UTF_8)
        );

        // When: 공통 HTTP 호출 실행
        ThrowingCallable action = () -> executor.execute(
                () -> {
                    throw failure;
                },
                exception -> {
                    throw new BusinessException(
                            CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                            exception
                    );
                }
        );

        // Then: 호출 대상 성공 응답 계약 위반의 502 변환
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertSoftly(softly -> {
                        softly.assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE);
                        softly.assertThat(exception.getCause()).isSameAs(failure);
                    });
                });
    }
}
