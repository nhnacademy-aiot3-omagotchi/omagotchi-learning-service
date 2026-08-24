package site.omagotchi.learningservice.team.infrastructure.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import site.omagotchi.learningservice.global.exception.ApiErrorResponse;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.http.ApiErrorResponseDecoder;
import site.omagotchi.learningservice.team.application.TeamErrorCode;

// Identity 계정 조회 4xx의 공개 Code·HTTP 상태 검증
@Component
@RequiredArgsConstructor
class IdentityAccountErrorResolver {

    private static final String AUTHENTICATION_REQUIRED =
            "AUTH_AUTHENTICATION_REQUIRED";
    private static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

    private final ApiErrorResponseDecoder errorDecoder;

    BusinessException resolveAccountLookupError(RestClientResponseException exception) {
        ApiErrorResponse response = errorDecoder.decode(exception);
        if (matches(exception, response, ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND)) {
            return new BusinessException(TeamErrorCode.ACCOUNT_NOT_FOUND, exception);
        }
        return resolveFailure(exception, response);
    }

    BusinessException resolveBatchLookupError(RestClientResponseException exception) {
        ApiErrorResponse response = errorDecoder.decode(exception);
        return resolveFailure(exception, response);
    }

    private BusinessException resolveFailure(
            RestClientResponseException exception,
            ApiErrorResponse response
    ) {
        if (matches(
                exception,
                response,
                AUTHENTICATION_REQUIRED,
                HttpStatus.UNAUTHORIZED
        )) {
            return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, exception);
        }
        return new BusinessException(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE, exception);
    }

    private boolean matches(
            RestClientResponseException exception,
            ApiErrorResponse response,
            String expectedCode,
            HttpStatus expectedStatus
    ) {
        return expectedCode.equals(response.code())
                && exception.getStatusCode().value() == expectedStatus.value();
    }
}
