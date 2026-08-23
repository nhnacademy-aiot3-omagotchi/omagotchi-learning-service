package site.omagotchi.learningservice.team.application.port;

/**
 * Identity 계정 조회 성공 응답을 Learning이 소비하는 Application Port 계약.
 * <p>Identity의 Java Type을 공유하지 않되 HTTP 계약의 실제 계정 상태를 손실 없이 유지한다.</p>
 */
public enum IdentityAccountState {
    ACTIVE,
    LOCKED,
    DISABLED,
    WITHDRAWN
}
