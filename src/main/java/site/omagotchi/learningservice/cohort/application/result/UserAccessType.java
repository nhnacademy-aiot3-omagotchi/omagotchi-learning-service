package site.omagotchi.learningservice.cohort.application.result;

/**
 * 로그인 사용자의 기본 화면을 결정하기 위한 최우선 접근 유형.
 *
 * <p>전역 역할과 기수 소속 역할은 서로 다른 축이므로, JWT 역할에 기수 관리자를
 * 섞지 않고 현재 DB 소속을 조합해 계산한다.</p>
 */
public enum UserAccessType {
    SYSTEM_ADMIN,
    COHORT_MANAGER,
    STUDENT,
    USER
}
