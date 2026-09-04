package site.omagotchi.learningservice.community.infrastructure;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

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
        verify(minioClient, times(2)).putObject(captor.capture());
        PutObjectArgs args = captor.getAllValues().get(0);
        PutObjectArgs thumbnailArgs = captor.getAllValues().get(1);
        assertAll(
                () -> assertEquals(BUCKET, args.bucket()),
                () -> assertEquals(stored.storageKey(), args.object()),
                () -> assertEquals("image/png", args.contentType().toString()),
                () -> assertTrue(stored.storageKey().startsWith("2026/08/08/")),
                () -> assertEquals("image.png", stored.originalFileName()),
                () -> assertEquals("image/png", stored.contentType()),
                () -> assertEquals(file.getSize(), stored.sizeBytes()),
                () -> assertEquals(2, stored.displayOrder()),
                () -> assertEquals(BUCKET, thumbnailArgs.bucket()),
                () -> assertTrue(thumbnailArgs.object().startsWith("_thumbnails/v1/480x300/2026/08/08/")),
                () -> assertTrue(thumbnailArgs.object().endsWith(".jpg")),
                () -> assertEquals("image/jpeg", thumbnailArgs.contentType().toString())
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
    @DisplayName("원본 업로드가 실패해도 시도한 객체를 정리한다")
    void cleansUpAttemptedObjectWhenOriginalUploadFails() throws Exception {
        // putObject 는 실패했을 때 원격 객체가 생겼는지 보장하지 않는다.
        // 성공한 것만 지우면 이 경로에서 고아 객체가 남는다.
        MinioException cause = new MinioException("original upload failed");
        willThrow(cause).given(minioClient).putObject(any());

        assertThrows(
                IllegalArgumentException.class,
                () -> storage().store(attachmentFile(
                        new MockMultipartFile("attachments", "image.png", "image/png", pngBytes()), 0
                ))
        );

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        // 썸네일은 시도조차 하지 않았으므로 원본만 지운다
        verify(minioClient, times(1)).removeObject(captor.capture());
        assertTrue(captor.getValue().object().startsWith("2026/08/08/"));
    }

    @Test
    @DisplayName("썸네일 업로드가 실패하면 저장된 원본과 파생 객체를 정리한다")
    void cleansUpObjectsWhenThumbnailUploadFails() throws Exception {
        MinioException cause = new MinioException("thumbnail upload failed");
        given(minioClient.putObject(any()))
                .willReturn(null)
                .willThrow(cause);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storage().store(attachmentFile(
                        new MockMultipartFile("attachments", "image.png", "image/png", pngBytes()), 0
                ))
        );

        assertEquals(cause, exception.getCause());
        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, times(2)).removeObject(captor.capture());
        assertAll(
                () -> assertTrue(captor.getAllValues().get(0).object().startsWith("_thumbnails/v1/480x300/")),
                () -> assertTrue(captor.getAllValues().get(1).object().startsWith("2026/08/08/"))
        );
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
    @DisplayName("파생 키의 썸네일 객체 스트림을 반환한다")
    void loadsThumbnailFromDerivedKey() throws Exception {
        byte[] content = {4, 5, 6};
        given(minioClient.getObject(any(GetObjectArgs.class))).willReturn(new GetObjectResponse(
                Headers.of(),
                BUCKET,
                null,
                "_thumbnails/v1/480x300/2026/08/08/key.jpg",
                new ByteArrayInputStream(content)
        ));

        var resource = storage().loadThumbnail("2026/08/08/key.png").orElseThrow();

        assertArrayEquals(content, resource.getInputStream().readAllBytes());
        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(captor.capture());
        assertEquals("_thumbnails/v1/480x300/2026/08/08/key.jpg", captor.getValue().object());
    }

    @Test
    @DisplayName("기존 객체에 썸네일이 없으면 empty를 반환한다")
    void returnsEmptyWhenLegacyAttachmentHasNoThumbnail() throws Exception {
        ErrorResponseException missing = mock(ErrorResponseException.class);
        ErrorResponse errorResponse = mock(ErrorResponse.class);
        given(missing.errorResponse()).willReturn(errorResponse);
        given(errorResponse.code()).willReturn("NoSuchKey");
        given(minioClient.getObject(any(GetObjectArgs.class))).willThrow(missing);

        assertTrue(storage().loadThumbnail("2026/08/08/key.png").isEmpty());
    }

    @Test
    @DisplayName("삭제는 같은 버킷의 객체를 지운다")
    void removesObject() throws Exception {
        storage().delete("2026/08/08/key.png");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, times(2)).removeObject(captor.capture());
        assertAll(
                () -> assertEquals(BUCKET, captor.getAllValues().get(0).bucket()),
                () -> assertEquals("2026/08/08/key.png", captor.getAllValues().get(0).object()),
                () -> assertEquals(
                        "_thumbnails/v1/480x300/2026/08/08/key.jpg",
                        captor.getAllValues().get(1).object()
                )
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
        verify(minioClient, times(2)).removeObject(any());
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
                new CommunityAttachmentPolicy(properties, clock),
                new CommunityAttachmentThumbnail()
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

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(34, 91, 180, 180));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
