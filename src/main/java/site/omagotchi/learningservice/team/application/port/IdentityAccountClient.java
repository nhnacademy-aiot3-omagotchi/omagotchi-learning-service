package site.omagotchi.learningservice.team.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Identity Service 계정 조회 파사드.
 * 계정은 논리 참조로만 다루며 서비스 간 DB 외래 키는 두지 않는다.
 */
public interface IdentityAccountClient {

    /**
     * 계정 상태를 조회한다 (GR-11).
     * 계정이 없으면 계정 미존재 오류로 중단하며, 공개 응답 코드는
     * {@code TEAM_ACCOUNT_NOT_FOUND}이다.
     */
    IdentityAccountState getState(UUID userId);

    /**
     * 표시명 배치 조회 (GR-15). accounts.name이 출처다.
     * 팀원 8명이어도 호출은 1회여야 한다.
     */
    Map<UUID, String> findDisplayNames(Collection<UUID> userIds);

    /** Learning이 확정한 후보 계정 범위 안에서 이름 또는 이메일로 최대 20개를 검색한다. */
    List<IdentityAccountView> search(String query, Collection<UUID> candidateIds);
}
