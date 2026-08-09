package site.omagotchi.learningservice.community.domain;

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
    ATTACHMENT_STORAGE_FAILED(
            ErrorType.INTERNAL,
            "COMMUNITY_ATTACHMENT_STORAGE_FAILED",
            "첨부파일 저장에 실패했습니다."
    );

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
