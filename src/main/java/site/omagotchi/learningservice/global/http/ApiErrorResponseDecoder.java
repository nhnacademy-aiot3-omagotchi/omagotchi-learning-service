package site.omagotchi.learningservice.global.http;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import site.omagotchi.learningservice.global.exception.ApiErrorResponse;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

// 내부 서비스가 반환한 공통 오류 JSON의 해석과 기본 계약 검증
@Component
public class ApiErrorResponseDecoder {

    public ApiErrorResponse decode(RestClientResponseException exception) {
        // 계약으로 정해진 형식으로 왔는지 검사
        ApiErrorResponse response;
        try {
            response = exception.getResponseBodyAs(ApiErrorResponse.class);
        } catch (RestClientException decodeFailure) {
            BusinessException contractViolation = new BusinessException(
                    CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                    "HTTP 오류 응답 JSON 해석 실패 status=" + exception.getStatusCode().value(),
                    exception
            );
            // json 변환 예외를 보존하기 위한 로그 및 디버깅 용도
            contractViolation.addSuppressed(decodeFailure);
            throw contractViolation;
        }

        // 공통 오류 본문의 필수 값 검사
        if (response == null
                || !StringUtils.hasText(response.code())
                || !StringUtils.hasText(response.message())
                || !StringUtils.hasText(response.path())
        ) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE, exception);
        }
        return response;
    }
}
