package site.omagotchi.learningservice.team.infrastructure.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import site.omagotchi.learningservice.global.exception.ApiErrorResponse;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.http.ApiErrorResponseDecoder;
import site.omagotchi.learningservice.team.application.TeamErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class IdentityAccountErrorResolverTest {

    private final ApiErrorResponseDecoder errorDecoder =
            mock(ApiErrorResponseDecoder.class);
    private final IdentityAccountErrorResolver errorResolver =
            new IdentityAccountErrorResolver(errorDecoder);

    @Test
    @DisplayName("Identity 계정 미존재 계약의 Learning 오류 변환")
    void resolvesAccountNotFound() {
        // Given: 공개 Code와 HTTP 상태가 일치하는 계정 미존재 응답
        RestClientResponseException exception = error(
                "ACCOUNT_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );

        // When & Then: Team 계정 미존재 오류
        assertThat(errorResolver.resolveAccountLookupError(exception).getErrorCode())
                .isEqualTo(TeamErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("Identity 서비스 Credential 거절의 503 변환")
    void resolvesAuthenticationFailure() {
        // Given: 공개 Code와 HTTP 상태가 일치하는 인증 실패 응답
        RestClientResponseException exception = error(
                "AUTH_AUTHENTICATION_REQUIRED",
                HttpStatus.UNAUTHORIZED
        );

        // When & Then: 호출 서비스 가용성 오류
        assertThat(errorResolver.resolveBatchLookupError(exception).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Identity 공개 Code와 HTTP 상태 불일치의 502 변환")
    void rejectsMismatchedStatus() {
        // Given: 계정 미존재 Code와 일치하지 않는 HTTP 상태
        RestClientResponseException exception = error(
                "ACCOUNT_NOT_FOUND",
                HttpStatus.BAD_REQUEST
        );

        // When & Then: 호출 계약 위반
        assertThat(errorResolver.resolveAccountLookupError(exception).getErrorCode())
                .isEqualTo(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("Identity 미등록 오류 Code의 502 변환")
    void rejectsUnknownErrorCode() {
        // Given: Learning이 소비하지 않는 Identity 오류 응답
        RestClientResponseException exception = error(
                "ACCOUNT_UNKNOWN_FAILURE",
                HttpStatus.BAD_REQUEST
        );

        // When & Then: 호출 계약 위반
        assertThat(errorResolver.resolveBatchLookupError(exception).getErrorCode())
                .isEqualTo(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE);
    }

    private RestClientResponseException error(String code, HttpStatus status) {
        RestClientResponseException exception = mock(RestClientResponseException.class);
        given(exception.getStatusCode()).willReturn(status);
        given(errorDecoder.decode(exception)).willReturn(new ApiErrorResponse(
                code,
                "오류",
                "/api/v1/internal/accounts",
                null
        ));
        return exception;
    }
}
