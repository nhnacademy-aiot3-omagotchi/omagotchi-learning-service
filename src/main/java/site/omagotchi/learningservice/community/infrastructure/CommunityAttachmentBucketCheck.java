package site.omagotchi.learningservice.community.infrastructure;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 기동 시 커뮤니티 첨부파일 버킷이 실제로 있는지 한 번 확인하고 결과를 로그로 남긴다.
 *
 * <p>버킷 이름 오타나 잘못된 자격증명은 그대로 두면 첫 업로드가 일어나는 순간에야 드러난다.
 * 배포 직후에 알 수 있도록 여기에서 미리 두드려 본다.</p>
 *
 * <p><b>실패해도 기동을 막지 않는다.</b> {@link site.omagotchi.learningservice.gamification.infrastructure.GamificationReferenceDataBootstrap}
 * 은 기준 데이터가 없으면 기동을 실패시키지만, 이쪽은 판단이 다르다. 첨부파일 저장소는 우리가
 * 소유하지 않는 공용 MinIO라, 그쪽이 잠깐 내려갔다고 커뮤니티 조회·글쓰기까지 못 하게 되는 편이
 * 더 나쁘다. 첨부파일 없이도 게시판의 나머지는 정상 동작한다.</p>
 *
 * <p>버킷은 이 확인을 통과하지 못해도 자동으로 만들지 않는다. 없는 버킷은 설정 실수이고,
 * 조용히 만들어 두면 오타 난 이름으로 운영이 흘러간다.</p>
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class CommunityAttachmentBucketCheck implements ApplicationRunner {

    private final MinioClient minioClient;
    private final CommunityAttachmentProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        check();
    }

    /**
     * 어떤 실패에도 예외를 밖으로 내보내지 않는다.
     */
    public void check() {
        String bucket = properties.bucket();
        try {
            if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                log.info("커뮤니티 첨부파일 버킷을 확인했습니다. bucket={}", bucket);
                return;
            }
            log.warn(
                    "커뮤니티 첨부파일 버킷이 없습니다. 첨부파일 업로드와 다운로드가 실패합니다."
                            + " bucket={} -- MinIO에 버킷을 만들거나 community.attachments.bucket을 확인해 주세요.",
                    bucket
            );
        } catch (Exception exception) {
            log.warn(
                    "커뮤니티 첨부파일 버킷을 확인하지 못했습니다."
                            + " bucket={} -- MinIO 주소와 자격증명(minio.endpoint, minio.access-key)을 확인해 주세요.",
                    bucket,
                    exception
            );
        }
    }
}
