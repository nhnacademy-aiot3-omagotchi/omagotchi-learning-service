package site.omagotchi.learningservice.community.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 애플리케이션 기동 완료를 기다리지 않고 버킷 점검 작업만 비동기로 시작한다. */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class CommunityAttachmentBucketCheckRunner implements ApplicationRunner {

    private final CommunityAttachmentBucketCheck bucketCheck;

    @Override
    public void run(ApplicationArguments args) {
        bucketCheck.checkAsync();
    }
}
