package site.omagotchi.learningservice.team.support;

import site.omagotchi.learningservice.team.application.AccountReader;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 테스트 전용 AccountReader. 계정 상태와 표시명을 직접 주입한다.
 * 등록되지 않은 userId는 NOT_FOUND다 — 기본값을 ACTIVE로 두면
 * "존재하지 않는 사용자 추가" 케이스를 영영 테스트할 수 없다.
 */
public class FakeAccountReader implements AccountReader {

    private final Map<UUID, AccountState> states = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    /**
     * 정상 계정을 하나 만들고 그 계정 id를 돌려준다.
     *
     * @return 생성된 계정 id. 팀원 추가 요청의 targetUserId로 그대로 쓴다
     */
    public UUID register(String name) {
        return register(name, AccountState.ACTIVE);
    }

    /**
     * 상태를 지정해 계정을 만든다.
     *
     * <p>탈퇴 계정을 처음부터 만들고 싶을 때 쓴다. 이미 만든 계정을 탈퇴시키려면
     * {@link #withdraw(UUID)}가 낫다 — 표시명이 유지되므로 "탈퇴 전에는 보이던 사람"
     * 시나리오를 그대로 이어갈 수 있다.</p>
     */
    public UUID register(String name, AccountState state) {
        UUID userId = UUID.randomUUID();
        states.put(userId, state);
        names.put(userId, name);
        return userId;
    }

    /**
     * 이미 등록된 계정을 탈퇴 상태로 바꾼다 (GR-11 검증용).
     *
     * <p>표시명은 남겨둔다. 실제 Identity Service도 소프트 삭제라 이름을 지우지 않으며,
     * "탈퇴한 사람이 아직 팀원 목록에 보이는" 상태를 재현할 수 있어야 한다.</p>
     */
    public void withdraw(UUID userId) {
        states.put(userId, AccountState.WITHDRAWN);
    }

    @Override
    public AccountState findState(UUID userId) {
        return states.getOrDefault(userId, AccountState.NOT_FOUND);
    }

    @Override
    public Map<UUID, String> findDisplayNames(Collection<UUID> userIds) {
        return userIds.stream()
                .distinct()
                .filter(names::containsKey)
                .collect(Collectors.toMap(userId -> userId, names::get));
    }
}