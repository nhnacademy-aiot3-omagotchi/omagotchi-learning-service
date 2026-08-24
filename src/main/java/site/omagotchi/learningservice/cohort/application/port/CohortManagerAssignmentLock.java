package site.omagotchi.learningservice.cohort.application.port;

import java.util.UUID;

/**
 * 동일 사용자의 기수 관리자 배치 명령을 트랜잭션 단위로 직렬화한다.
 */
public interface CohortManagerAssignmentLock {

    void acquire(UUID userId);
}
