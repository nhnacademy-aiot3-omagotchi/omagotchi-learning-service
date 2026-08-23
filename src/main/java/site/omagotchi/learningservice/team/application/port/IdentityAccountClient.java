package site.omagotchi.learningservice.team.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Identity Service 계정 조회 파사드.
 * 계정은 논리 참조로만 다루며 서비스 간 DB 외래 키는 두지 않는다.
 */
public interface IdentityAccountClient {

    /**
     * 계정 상태를 조회한다 (GR-11).
     * 계정이 없으면 {@code TEAM_ACCOUNT_NOT_FOUND} 오류로 중단한다.
     */
    IdentityAccountState getState(UUID userId);

    /**
     * 표시명 배치 조회 (GR-15). accounts.name이 출처다.
     * 팀원 8명이어도 호출은 1회여야 한다.
     */
    Map<UUID, String> findDisplayNames(Collection<UUID> userIds);
}
