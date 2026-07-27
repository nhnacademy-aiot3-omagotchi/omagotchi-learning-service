package site.omagotchi.learningservice.study.application.port;

/**
 * 공부 기록에 영향을 주는 쓰기 명령을 기수 소속 단위로 직렬화한다.
 */
public interface StudyWriteLock {

    // 현재 트랜잭션이 끝날 때까지 기수 소속의 배타 쓰기 잠금을 획득한다.
    void acquire(long cohortMembershipId);
}
