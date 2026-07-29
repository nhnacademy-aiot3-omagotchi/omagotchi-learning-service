package site.omagotchi.learningservice.team.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Identity Service 계정 조회 파사드.
 * 계정은 논리 참조로만 다루며 서비스 간 DB 외래 키는 두지 않는다.
 */
public interface AccountReader {

    /**
     * 계정이 존재하고 탈퇴하지 않았는지 확인한다 (GR-11).
     * 미존재는 404, 탈퇴는 409로 구분해야 하므로 boolean이 아니라 3-state다.
     */
    AccountState findState(UUID userId);

    /**
     * 표시명 배치 조회 (GR-15). accounts.name이 출처다.
     * 팀원 8명이어도 호출은 1회여야 한다.
     */
    Map<UUID, String> findDisplayNames(Collection<UUID> userIds);

    enum AccountState {
        ACTIVE,
        WITHDRAWN,
        NOT_FOUND
    }
}