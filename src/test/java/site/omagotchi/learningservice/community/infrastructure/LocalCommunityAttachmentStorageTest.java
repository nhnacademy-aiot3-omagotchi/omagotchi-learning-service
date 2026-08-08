package site.omagotchi.learningservice.community.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.domain.CommunityErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("로컬 커뮤니티 첨부파일 저장소")
class LocalCommunityAttachmentStorageTest {

    @TempDir
    Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("검증된 이미지 파일을 서버 생성 storageKey로 저장한다")
    void storesValidImageWithGeneratedStorageKey() {
        LocalCommunityAttachmentStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile(
                "attachments",
                "image.png",
                "image/png",
                pngBytes()
        );

        var result = storage.store(new CommunityAttachmentFile(file, 0));

        assertAll(
                () -> assertTrue(result.storageKey().startsWith("2026/08/08/")),
                () -> assertTrue(result.storageKey().endsWith(".png")),
                () -> assertEquals("image.png", result.originalFileName()),
                () -> assertEquals("image/png", result.contentType()),
                () -> assertEquals(file.getSize(), result.sizeBytes()),
                () -> assertTrue(Files.exists(tempDir.resolve(result.storageKey())))
        );
    }

    @Test
    @DisplayName("빈 파일을 거절한다")
    void rejectsEmptyFile() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> storage().store(new CommunityAttachmentFile(
                        new MockMultipartFile("attachments", "image.png", "image/png", new byte[0]),
                        0
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    @Test
    @DisplayName("허용되지 않은 확장자를 거절한다")
    void rejectsDisallowedExtension() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> storage().store(new CommunityAttachmentFile(
                        new MockMultipartFile("attachments", "image.webp", "image/webp", pngBytes()),
                        0
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    @Test
    @DisplayName("파일 시그니처와 확장자가 맞지 않으면 거절한다")
    void rejectsMismatchedDetectedContentType() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> storage().store(new CommunityAttachmentFile(
                        new MockMultipartFile("attachments", "image.jpg", "image/jpeg", pngBytes()),
                        0
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    @Test
    @DisplayName("경로 탐색 파일명을 거절한다")
    void rejectsPathTraversalFilename() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> storage().store(new CommunityAttachmentFile(
                        new MockMultipartFile("attachments", "../image.png", "image/png", pngBytes()),
                        0
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
    }

    private LocalCommunityAttachmentStorage storage() {
        return new LocalCommunityAttachmentStorage(
                new CommunityAttachmentProperties(
                        tempDir,
                        DataSize.ofMegabytes(5),
                        5,
                        List.of("jpg", "jpeg", "png", "gif"),
                        List.of("image/jpeg", "image/png", "image/gif")
                ),
                clock
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
