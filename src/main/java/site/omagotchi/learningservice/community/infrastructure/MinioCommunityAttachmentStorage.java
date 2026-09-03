package site.omagotchi.learningservice.community.infrastructure;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.attachment.StoredCommunityAttachment;

import java.io.IOException;
import java.io.InputStream;

/**
 * 커뮤니티 첨부파일을 MinIO 버킷에 저장한다.
 *
 * <p>업로드 규칙 검증과 객체 키 생성은 {@link CommunityAttachmentPolicy}가 맡고,
 * 이 클래스는 바이트를 옮기는 일만 한다.</p>
 *
 * <p>다운로드는 서버가 중계한다. 게시글 가시성 검증을 거친 요청에만 스트림을 열어 주므로,
 * 버킷은 비공개로 두고 자격증명은 서버만 갖는다.</p>
 *
 * <p>저장소 실패는 {@code BusinessException}으로 감싸지 않는다. 클라이언트가 분기할 외부 계약이
 * 없는 기술 실패이고, {@code BusinessException}은 {@code ErrorType.INTERNAL} 전달을 막는
 * 가드가 있어 오히려 다른 예외로 바뀐다. 그대로 전파해서 {@code GlobalExceptionHandler}가
 * 스택 트레이스를 남기고 500으로 옮기게 둔다.</p>
 */
@Component
@RequiredArgsConstructor
public class MinioCommunityAttachmentStorage implements CommunityAttachmentStorage {

    private final MinioClient minioClient;
    private final CommunityAttachmentProperties properties;
    private final CommunityAttachmentPolicy policy;

    @Override
    public StoredCommunityAttachment store(CommunityAttachmentFile attachmentFile) {
        CommunityAttachmentTarget target = policy.prepare(attachmentFile);

        try (InputStream inputStream = attachmentFile.openStream()) {
            // 크기를 넘겨야 클라이언트가 전체를 메모리에 담지 않고 바로 흘려보낸다.
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(target.storageKey())
                    .stream(inputStream, attachmentFile.sizeBytes(), null)
                    .contentType(target.contentType())
                    .build());
        } catch (MinioException | IOException exception) {
            throw new IllegalArgumentException(
                    "첨부파일 업로드에 실패했습니다. bucket=%s, key=%s".formatted(properties.bucket(), target.storageKey()),
                    exception
            );
        }

        return new StoredCommunityAttachment(
                target.storageKey(),
                target.originalFileName(),
                target.contentType(),
                attachmentFile.sizeBytes(),
                attachmentFile.displayOrder()
        );
    }

    /**
     * 스트림을 열어서 돌려주고, 소비와 닫기는 응답을 쓰는 쪽에 맡긴다.
     * 응답의 Content-Length는 DB에 기록된 size_bytes를 쓰므로 여기에서 길이를 재지 않는다.
     */
    @Override
    public Resource load(String storageKey) {
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(storageKey)
                    .build());
            return new InputStreamResource(response);
        } catch (MinioException exception) {
            throw new IllegalArgumentException(
                    "첨부파일을 읽지 못했습니다. bucket=%s, key=%s".formatted(properties.bucket(), storageKey),
                    exception
            );
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(storageKey)
                    .build());
        } catch (MinioException exception) {
            throw new IllegalArgumentException(
                    "첨부파일을 지우지 못했습니다. bucket=%s, key=%s".formatted(properties.bucket(), storageKey),
                    exception
            );
        }
    }
}
