package site.omagotchi.learningservice.community.infrastructure;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@DisplayName("커뮤니티 첨부파일 버킷 기동 확인")
@ExtendWith(MockitoExtension.class)
class CommunityAttachmentBucketCheckTest {

    private static final String BUCKET = "community-attachments";

    @Mock
    private MinioClient minioClient;

    @Test
    @DisplayName("설정된 버킷 이름으로 존재 여부를 확인한다")
    void checksConfiguredBucket() throws Exception {
        given(minioClient.bucketExists(any(BucketExistsArgs.class))).willReturn(true);

        check().check();

        ArgumentCaptor<BucketExistsArgs> captor = ArgumentCaptor.forClass(BucketExistsArgs.class);
        verify(minioClient).bucketExists(captor.capture());
        assertEquals(BUCKET, captor.getValue().bucket());
    }

    @Test
    @DisplayName("버킷이 없어도 기동을 막지 않는다")
    void doesNotFailWhenBucketMissing() throws Exception {
        given(minioClient.bucketExists(any(BucketExistsArgs.class))).willReturn(false);

        assertDoesNotThrow(() -> check().check());
    }

    @Test
    @DisplayName("MinIO에 닿지 못해도 기동을 막지 않는다")
    void doesNotFailWhenMinioUnreachable() throws Exception {
        willThrow(new MinioException("connection refused"))
                .given(minioClient).bucketExists(any(BucketExistsArgs.class));

        assertDoesNotThrow(() -> check().check());
    }

    private CommunityAttachmentBucketCheck check() {
        return new CommunityAttachmentBucketCheck(
                minioClient,
                new CommunityAttachmentProperties(
                        BUCKET,
                        DataSize.ofMegabytes(5),
                        5,
                        List.of("jpg", "jpeg", "png", "gif"),
                        List.of("image/jpeg", "image/png", "image/gif")
                )
        );
    }
}
