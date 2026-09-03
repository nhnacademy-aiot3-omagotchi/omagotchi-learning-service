package site.omagotchi.learningservice.community.infrastructure;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import okhttp3.Headers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@DisplayName("MinIO 커뮤니티 첨부파일 저장소")
@ExtendWith(MockitoExtension.class)
class MinioCommunityAttachmentStorageTest {

    private static final String BUCKET = "community-attachments";

    @Mock
    private MinioClient minioClient;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("검증에서 판정한 MIME과 서버 생성 키로 버킷에 올린다")
    void uploadsWithDetectedContentTypeAndGeneratedKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile("attachments", "image.png", "image/png", pngBytes());

        var stored = storage().store(attachmentFile(file, 2));

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        PutObjectArgs args = captor.getValue();
        assertAll(
                () -> assertEquals(BUCKET, args.bucket()),
                () -> assertEquals(stored.storageKey(), args.object()),
                () -> assertEquals("image/png", args.contentType().toString()),
                () -> assertTrue(stored.storageKey().startsWith("2026/08/08/")),
                () -> assertEquals("image.png", stored.originalFileName()),
                () -> assertEquals("image/png", stored.contentType()),
                () -> assertEquals(file.getSize(), stored.sizeBytes()),
                () -> assertEquals(2, stored.displayOrder())
        );
    }

    @Test
    @DisplayName("검증에 걸린 파일은 버킷에 올리지 않는다")
    void doesNotUploadRejectedFile() throws Exception {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> storage().store(attachmentFile(
                        new MockMultipartFile("attachments", "image.webp", "image/webp", pngBytes()), 0
                ))
        );

        assertEquals(CommunityErrorCode.INVALID_ATTACHMENT, exception.getErrorCode());
        verify(minioClient, org.mockito.Mockito.never()).putObject(any());
    }

    @Test
    @DisplayName("업로드 실패는 BusinessException으로 감싸지 않고 전파한다")
    void propagatesUploadFailure() throws Exception {
        MinioException cause = new MinioException("upload failed");
        willThrow(cause).given(minioClient).putObject(any());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storage().store(attachmentFile(
                        new MockMultipartFile("attachments", "image.png", "image/png", pngBytes()), 0
                ))
        );

        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("객체 스트림을 그대로 흘려보낸다")
    void streamsObjectWithoutBuffering() throws Exception {
        byte[] content = {1, 2, 3};
        given(minioClient.getObject(any(GetObjectArgs.class))).willReturn(new GetObjectResponse(
                Headers.of(),
                BUCKET,
                null,
                "2026/08/08/key.png",
                new ByteArrayInputStream(content)
        ));

        var resource = storage().load("2026/08/08/key.png");

        assertArrayEquals(content, resource.getInputStream().readAllBytes());
    }

    @Test
    @DisplayName("삭제는 같은 버킷의 객체를 지운다")
    void removesObject() throws Exception {
        storage().delete("2026/08/08/key.png");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertAll(
                () -> assertEquals(BUCKET, captor.getValue().bucket()),
                () -> assertEquals("2026/08/08/key.png", captor.getValue().object())
        );
    }

    @Test
    @DisplayName("삭제 실패도 그대로 전파한다")
    void propagatesDeleteFailure() throws Exception {
        MinioException cause = new MinioException("remove failed");
        willThrow(cause).given(minioClient).removeObject(any());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storage().delete("2026/08/08/key.png")
        );

        assertEquals(cause, exception.getCause());
    }

    private MinioCommunityAttachmentStorage storage() {
        CommunityAttachmentProperties properties = new CommunityAttachmentProperties(
                BUCKET,
                DataSize.ofMegabytes(5),
                5,
                List.of("jpg", "jpeg", "png", "gif"),
                List.of("image/jpeg", "image/png", "image/gif")
        );
        return new MinioCommunityAttachmentStorage(
                minioClient,
                properties,
                new CommunityAttachmentPolicy(properties, clock)
        );
    }

    private CommunityAttachmentFile attachmentFile(MockMultipartFile file, int displayOrder) {
        return new CommunityAttachmentFile(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                displayOrder,
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
