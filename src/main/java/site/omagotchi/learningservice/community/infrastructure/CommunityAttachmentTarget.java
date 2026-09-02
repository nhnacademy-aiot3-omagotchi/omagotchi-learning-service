package site.omagotchi.learningservice.community.infrastructure;

/**
 * 검증을 통과해 저장 준비가 끝난 첨부파일. 아직 아무것도 저장되지 않은 상태다.
 *
 * @param storageKey       저장소가 쓸 객체 키. 서버가 만들며 사용자 입력이 섞이지 않는다.
 * @param originalFileName 다운로드할 때 보여줄 원래 이름
 * @param contentType      파일 헤더에서 판정한 MIME. 클라이언트가 보낸 값이 아니다.
 */
public record CommunityAttachmentTarget(
        String storageKey,
        String originalFileName,
        String contentType
) {
}
