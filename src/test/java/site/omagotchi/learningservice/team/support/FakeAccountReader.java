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

    public UUID register(String name) {
        return register(name, AccountState.ACTIVE);
    }

    public UUID register(String name, AccountState state) {
        UUID userId = UUID.randomUUID();
        states.put(userId, state);
        names.put(userId, name);
        return userId;
    }

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