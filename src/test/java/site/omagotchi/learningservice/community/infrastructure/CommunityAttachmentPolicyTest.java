package site.omagotchi.learningservice.community.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import site.omagotchi.learningservice.community.application.CommunityErrorCode;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("커뮤니티 첨부파일 업로드 정책")
class CommunityAttachmentPolicyTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("검증을 통과하면 서버가 만든 객체 키를 부여한다")
    void assignsGeneratedStorageKey() {
        MockMultipartFile file = new MockMultipartFile("attachments", "image.png", "image/png", pngBytes());

        var target = policy().prepare(attachmentFile(file));

        assertAll(
                () -> assertTrue(target.storageKey().startsWith("2026/08/08/")),
                () -> assertTrue(target.storageKey().endsWith(".png")),
                () -> assertEquals("image.png", target.originalFileName()),
                () -> assertEquals("image/png", target.contentType())
        );
    }

    @Test
    @DisplayName("객체 키에 원본 파일명을 넣지 않는다")
    void doesNotPutOriginalFileNameInStorageKey() {
        MockMultipartFile file = new MockMultipartFile("attachments", "내 사진.png", "image/png", pngBytes());

        var target = policy().prepare(attachmentFile(file));

        assertAll(
                () -> assertTrue(target.storageKey().matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.png")),
                () -> assertEquals("내 사진.png", target.originalFileName())
        );
    }

    @Test
    @DisplayName("빈 파일을 거절한다")
    void rejectsEmptyFile() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> policy().prepare(new CommunityAttachmentFile(
                        "image.png",
                        "image/png",
                        0,
                        0,
                        () -> new ByteArrayInputStream(new byte[0])
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    @Test
    @DisplayName("최대 크기를 넘으면 거절한다")
    void rejectsOversizedFile() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> policy().prepare(new CommunityAttachmentFile(
                        "image.png",
                        "image/png",
                        DataSize.ofMegabytes(6).toBytes(),
                        0,
                        () -> new ByteArrayInputStream(pngBytes())
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    @Test
    @DisplayName("허용되지 않은 확장자를 거절한다")
    void rejectsDisallowedExtension() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> policy().prepare(attachmentFile(
                        new MockMultipartFile("attachments", "image.webp", "image/webp", pngBytes())
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    @Test
    @DisplayName("확장자만 바꾼 파일은 시그니처 검사에서 거절한다")
    void rejectsMismatchedDetectedContentType() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> policy().prepare(attachmentFile(
                        new MockMultipartFile("attachments", "image.jpg", "image/jpeg", pngBytes())
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    @Test
    @DisplayName("클라이언트가 보낸 Content-Type은 신뢰하지 않는다")
    void ignoresClientContentType() {
        MockMultipartFile lying = new MockMultipartFile(
                "attachments", "image.png", "application/x-executable", pngBytes()
        );

        var target = policy().prepare(attachmentFile(lying));

        assertEquals("image/png", target.contentType());
    }

    @Test
    @DisplayName("경로 탐색 파일명을 거절한다")
    void rejectsPathTraversalFilename() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> policy().prepare(attachmentFile(
                        new MockMultipartFile("attachments", "../image.png", "image/png", pngBytes())
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    @Test
    @DisplayName("시그니처를 다 담지 못하는 짧은 파일을 거절한다")
    void rejectsTruncatedFile() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> policy().prepare(new CommunityAttachmentFile(
                        "image.png",
                        "image/png",
                        4,
                        0,
                        () -> new ByteArrayInputStream(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    private CommunityAttachmentPolicy policy() {
        return new CommunityAttachmentPolicy(
                new CommunityAttachmentProperties(
                        "community-attachments",
                        DataSize.ofMegabytes(5),
                        5,
                        List.of("jpg", "jpeg", "png", "gif"),
                        List.of("image/jpeg", "image/png", "image/gif")
                ),
                clock
        );
    }

    private CommunityAttachmentFile attachmentFile(MockMultipartFile file) {
        return new CommunityAttachmentFile(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                0,
                file::getInputStream
        );
    }

    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
    }
}
