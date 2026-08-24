package site.omagotchi.learningservice.global.http;

import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

import java.util.function.Function;
import java.util.function.Supplier;

// 외부 HTTP 호출의 공통 전송 실패 변환과 호출별 4xx 처리 위임
@Component
public class RestClientCallExecutor {

    private static final String NO_SERVICE_INSTANCE_MESSAGE_PREFIX =
            "No instances available for ";

    public <T> T execute(
            Supplier<T> request,
            Function<RestClientResponseException, T> clientErrorHandler
    ) {
        try {
            return request.get();

        } catch (ResourceAccessException exception) {
            // HTTP 응답 미수신: 연결 실패·Timeout의 503 변환
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, exception);

        } catch (IllegalStateException exception) {
            // Discovery Instance 부재: 전용 예외 Type 부재에 따른 제한적 503 변환
            if (isMissingServiceInstance(exception)) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, exception);
            }
            throw exception;

        } catch (RestClientResponseException exception) {
            // 호출 대상 5xx: 세부 내용 비공개와 503 변환
            if (exception.getStatusCode().is5xxServerError()) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, exception);
            }
            // 호출 대상 4xx: Endpoint별 결과 복구 또는 예외 변환 위임
            return clientErrorHandler.apply(exception);

        } catch (RestClientException exception) {
            // 2xx 응답 본문·Content-Type 계약 위반만 502 변환
            if (exception instanceof UnknownContentTypeException
                    || exception.getCause() instanceof HttpMessageNotReadableException) {
                throw new BusinessException(
                        CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                        exception
                );
            }
            throw exception;
        }
    }

    // 일반 IllegalStateException 오분류 방지를 위한 메시지·발생 위치 동시 확인
    private static boolean isMissingServiceInstance(IllegalStateException exception) {
        String message = exception.getMessage();
        if (message == null || !message.startsWith(NO_SERVICE_INSTANCE_MESSAGE_PREFIX)) {
            return false;
        }
        for (StackTraceElement stackTraceElement : exception.getStackTrace()) {
            if (BlockingLoadBalancerClient.class.getName().equals(stackTraceElement.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
