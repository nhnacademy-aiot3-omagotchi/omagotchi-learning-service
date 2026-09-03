package site.omagotchi.learningservice.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 접속 설정.
 *
 * <p>버킷 이름은 기능마다 다르므로 여기 두지 않는다.
 * 커뮤니티 첨부파일은 {@code community.attachments.bucket}을 쓴다.</p>
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey
) {
}
