package site.omagotchi.learningservice.community.infrastructure;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.attachment.StoredCommunityAttachment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

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
    private final CommunityAttachmentThumbnail thumbnail;

    @Override
    public StoredCommunityAttachment store(CommunityAttachmentFile attachmentFile) {
        CommunityAttachmentTarget target = policy.prepare(attachmentFile);
        CommunityAttachmentThumbnail.Generated generatedThumbnail =
                thumbnail.generate(target.storageKey(), attachmentFile);

        boolean originalStored = false;
        try (InputStream inputStream = attachmentFile.openStream();
             InputStream thumbnailStream = new ByteArrayInputStream(generatedThumbnail.bytes())) {
            // 크기를 넘겨야 클라이언트가 전체를 메모리에 담지 않고 바로 흘려보낸다.
            putObject(target.storageKey(), target.contentType(), attachmentFile.sizeBytes(), inputStream);
            originalStored = true;
            putObject(
                    generatedThumbnail.storageKey(),
                    CommunityAttachmentThumbnail.CONTENT_TYPE,
                    generatedThumbnail.bytes().length,
                    thumbnailStream
            );
        } catch (MinioException | IOException exception) {
            if (originalStored) {
                removeQuietly(generatedThumbnail.storageKey(), exception);
                removeQuietly(target.storageKey(), exception);
            }
            throw new IllegalArgumentException(
                    "첨부파일 또는 썸네일 업로드에 실패했습니다. bucket=%s, key=%s"
                            .formatted(properties.bucket(), target.storageKey()),
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
            return loadObject(storageKey);
        } catch (MinioException exception) {
            throw new IllegalArgumentException(
                    "첨부파일을 읽지 못했습니다. bucket=%s, key=%s".formatted(properties.bucket(), storageKey),
                    exception
            );
        }
    }

    /**
     * 신규 첨부파일은 파생 키의 JPEG를 반환한다. 배포 이전 객체에 썸네일이 없는 경우만
     * empty로 돌려서 서비스가 원본을 미리보기로 사용할 수 있게 한다.
     */
    @Override
    public Optional<Resource> loadThumbnail(String storageKey) {
        String thumbnailStorageKey = thumbnail.storageKey(storageKey);
        try {
            return Optional.of(loadObject(thumbnailStorageKey));
        } catch (ErrorResponseException exception) {
            if (isMissingObject(exception)) {
                return Optional.empty();
            }
            throw loadFailure(thumbnailStorageKey, exception);
        } catch (MinioException exception) {
            throw loadFailure(thumbnailStorageKey, exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        IllegalArgumentException failure = null;
        try {
            removeObject(storageKey);
        } catch (MinioException exception) {
            failure = deleteFailure(storageKey, exception);
        }

        String thumbnailStorageKey = thumbnail.storageKey(storageKey);
        try {
            removeObject(thumbnailStorageKey);
        } catch (MinioException exception) {
            IllegalArgumentException thumbnailFailure = deleteFailure(thumbnailStorageKey, exception);
            if (failure == null) {
                failure = thumbnailFailure;
            } else {
                failure.addSuppressed(thumbnailFailure);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private void putObject(String storageKey, String contentType, long size, InputStream inputStream)
            throws MinioException {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.bucket())
                .object(storageKey)
                .stream(inputStream, size, null)
                .contentType(contentType)
                .build());
    }

    private Resource loadObject(String storageKey) throws MinioException {
        GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(storageKey)
                .build());
        return new InputStreamResource(response);
    }

    private void removeObject(String storageKey) throws MinioException {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.bucket())
                .object(storageKey)
                .build());
    }

    private void removeQuietly(String storageKey, Exception uploadFailure) {
        try {
            removeObject(storageKey);
        } catch (MinioException cleanupFailure) {
            uploadFailure.addSuppressed(cleanupFailure);
        }
    }

    private boolean isMissingObject(ErrorResponseException exception) {
        String code = exception.errorResponse() == null ? null : exception.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
    }

    private IllegalArgumentException loadFailure(String storageKey, MinioException exception) {
        return new IllegalArgumentException(
                "첨부파일을 읽지 못했습니다. bucket=%s, key=%s".formatted(properties.bucket(), storageKey),
                exception
        );
    }

    private IllegalArgumentException deleteFailure(String storageKey, MinioException exception) {
        return new IllegalArgumentException(
                "첨부파일을 지우지 못했습니다. bucket=%s, key=%s".formatted(properties.bucket(), storageKey),
                exception
        );
    }
}
