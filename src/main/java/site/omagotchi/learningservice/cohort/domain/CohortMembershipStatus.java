package site.omagotchi.learningservice.cohort.domain;

/**
 * 기수 소속 상태.
 * v1은 일시 비활성 상태 없이 신청, 승인, 거절, 종료만 관리한다.
 */
public enum CohortMembershipStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    ENDED
}
