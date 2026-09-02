package site.omagotchi.learningservice.community.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.community.application.CommunityErrorCode;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 첨부파일 업로드 규칙을 강제하고 저장소가 쓸 객체 키를 만든다.
 *
 * <p>저장소 종류와 무관한 정책이라 구현체(MinIO 등)와 분리했다. 확장자와 실제 파일
 * 헤더(magic bytes)를 함께 검증하므로, 확장자만 {@code .png}로 바꾼 파일은 걸러진다.
 * 클라이언트가 보낸 Content-Type은 신뢰하지 않는다.</p>
 *
 * <p>객체 키에는 사용자 입력을 넣지 않는다. 사용자 파일명을 키로 쓰면 같은 이름을 두 번
 * 올릴 때 덮어써지고, 인코딩 문제도 따라온다.</p>
 */
@Component
@RequiredArgsConstructor
public class CommunityAttachmentPolicy {

    private static final Map<String, Set<String>> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/gif", Set.of("gif")
    );

    // JPEG(3) · PNG(8) · GIF(6) 시그니처를 모두 담을 수 있는 최소 길이.
    private static final int HEADER_LENGTH = 12;

    private final CommunityAttachmentProperties properties;
    private final Clock clock;

    public CommunityAttachmentTarget prepare(CommunityAttachmentFile attachmentFile) {
        String originalFileName = safeOriginalFileName(attachmentFile.originalFileName());
        String extension = extension(originalFileName);
        validateExtension(extension);
        validateSize(attachmentFile.sizeBytes());
        String detectedContentType = detectContentType(header(attachmentFile));
        validateContentType(detectedContentType, extension);

        return new CommunityAttachmentTarget(
                storageKey(extension),
                originalFileName,
                detectedContentType
        );
    }

    private String safeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()
                || originalFileName.contains("/")
                || originalFileName.contains("\\")
                || originalFileName.contains("\0")
                || originalFileName.contains("..")) {
            throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
        }
        return originalFileName.trim();
    }

    private String extension(String originalFileName) {
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFileName.length() - 1) {
            throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
        }
        return originalFileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension) {
        boolean allowed = properties.allowedExtensions().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(extension::equals);
        if (!allowed) {
            throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
        }
    }

    private void validateSize(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > properties.maxFileSize().toBytes()) {
            throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
        }
    }

    /**
     * 스트림은 한 번에 12바이트를 다 주지 않을 수 있으므로 readNBytes로 채운다.
     * 채우지 못하면 이미지로 볼 수 없는 크기이므로 거절한다.
     */
    private byte[] header(CommunityAttachmentFile file) {
        try (InputStream inputStream = file.openStream()) {
            byte[] header = inputStream.readNBytes(HEADER_LENGTH);
            if (header.length < HEADER_LENGTH) {
                throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
            }
            return header;
        } catch (IOException exception) {
            throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT, exception);
        }
    }

    private String detectContentType(byte[] header) {
        if ((header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return "image/png";
        }
        if (header[0] == 0x47
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x38
                && (header[4] == 0x37 || header[4] == 0x39)
                && header[5] == 0x61) {
            return "image/gif";
        }
        throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
    }

    private void validateContentType(String contentType, String extension) {
        if (!properties.allowedContentTypes().contains(contentType)
                || !EXTENSIONS_BY_CONTENT_TYPE.getOrDefault(contentType, Set.of()).contains(extension)) {
            throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
        }
    }

    /**
     * 날짜 prefix는 폴더가 아니다. 객체 저장소의 키는 평평하고 {@code /}는 이름의 일부라,
     * 키를 고르게 흩는 역할을 한다.
     */
    private String storageKey(String extension) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return "%04d/%02d/%02d/%s.%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );
    }
}
