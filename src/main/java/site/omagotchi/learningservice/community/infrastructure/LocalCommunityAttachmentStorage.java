package site.omagotchi.learningservice.community.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentFile;
import site.omagotchi.learningservice.community.application.attachment.CommunityAttachmentStorage;
import site.omagotchi.learningservice.community.application.attachment.StoredCommunityAttachment;
import site.omagotchi.learningservice.community.domain.CommunityErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 커뮤니티 이미지 첨부파일을 로컬 파일시스템에 저장하는 최소 저장소 구현이다.
 *
 * <p>클라이언트 파일명은 저장 경로로 사용하지 않고, 확장자와 실제 파일 헤더 기반 MIME을 함께 검증한다.</p>
 */
@Component
@RequiredArgsConstructor
public class LocalCommunityAttachmentStorage implements CommunityAttachmentStorage {

    private static final Map<String, Set<String>> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/gif", Set.of("gif")
    );

    private final CommunityAttachmentProperties properties;
    private final Clock clock;

    @Override
    public StoredCommunityAttachment store(CommunityAttachmentFile attachmentFile) {
        String originalFileName = safeOriginalFileName(attachmentFile.originalFileName());
        String extension = extension(originalFileName);
        validateExtension(extension);
        validateSize(attachmentFile.sizeBytes());
        byte[] header = header(attachmentFile);
        String detectedContentType = detectContentType(header);
        validateContentType(detectedContentType, extension);

        String storageKey = storageKey(extension);
        Path targetPath = targetPath(storageKey);
        try {
            Files.createDirectories(targetPath.getParent());
            try (InputStream inputStream = attachmentFile.openStream()) {
                Files.copy(inputStream, targetPath);
            }
        } catch (IOException exception) {
            throw new BusinessException(CommunityErrorCode.ATTACHMENT_STORAGE_FAILED, exception);
        }

        return new StoredCommunityAttachment(
                storageKey,
                originalFileName,
                detectedContentType,
                attachmentFile.sizeBytes(),
                attachmentFile.displayOrder()
        );
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(targetPath(storageKey));
        } catch (IOException exception) {
            throw new BusinessException(CommunityErrorCode.ATTACHMENT_STORAGE_FAILED, exception);
        }
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

    private byte[] header(CommunityAttachmentFile file) {
        byte[] header = new byte[12];
        try (InputStream inputStream = file.openStream()) {
            int read = inputStream.read(header);
            if (read <= 0) {
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

    private Path targetPath(String storageKey) {
        Path root = properties.storageRoot().toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(CommunityErrorCode.INVALID_ATTACHMENT);
        }
        return target;
    }
}
