package site.omagotchi.learningservice.global.exception;

public record ApiErrorResponse(
        String code,
        String message,
        String path,
        String requestId
) {
}
