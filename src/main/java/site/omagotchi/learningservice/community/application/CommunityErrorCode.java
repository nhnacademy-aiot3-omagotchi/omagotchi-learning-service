package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

@RequiredArgsConstructor
public enum CommunityErrorCode implements ErrorCode {

    POST_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "COMMUNITY_POST_NOT_FOUND",
            "게시글을 찾을 수 없습니다."
    ),
    INVALID_PAGE_REQUEST(
            ErrorType.INVALID_INPUT,
            "COMMUNITY_INVALID_PAGE_REQUEST",
            "페이지 요청값이 올바르지 않습니다."
    ),
    INVALID_POST_REQUEST(
            ErrorType.INVALID_INPUT,
            "COMMUNITY_INVALID_POST_REQUEST",
            "게시글 요청값이 올바르지 않습니다."
    ),
    POST_ACCESS_DENIED(
            ErrorType.AUTHORIZATION,
            "COMMUNITY_POST_ACCESS_DENIED",
            "게시글 권한이 없습니다."
    ),
    INVALID_ATTACHMENT(
            ErrorType.INVALID_INPUT,
            "COMMUNITY_INVALID_ATTACHMENT",
            "첨부파일이 올바르지 않습니다."
    ),
    ATTACHMENT_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "COMMUNITY_ATTACHMENT_NOT_FOUND",
            "첨부파일을 찾을 수 없습니다."
    );

    // 저장소 자체의 실패(객체 스토리지 장애 등)에는 전용 코드를 두지 않는다.
    // BusinessException은 ErrorType.INTERNAL을 전달할 수 없고(의도된 가드), 이 실패는
    // 클라이언트가 분기할 외부 계약도 없다 -- 그대로 전파해
    // GlobalExceptionHandler의 마지막 경계에서 500으로 옮긴다.

    private final ErrorType type;
    private final String code;
    private final String message;

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
