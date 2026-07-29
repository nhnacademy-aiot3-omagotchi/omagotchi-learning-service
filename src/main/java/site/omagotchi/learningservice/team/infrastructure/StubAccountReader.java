package site.omagotchi.learningservice.team.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.team.application.AccountReader;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TODO: Identity Service 연동 전까지의 임시 구현.
 *
 * 실제 구현이 호출할 계약:
 *   GET  /internal/accounts/{userId}/state   → ACTIVE | WITHDRAWN | NOT_FOUND
 *   POST /internal/accounts/names            → { userId: name }
 * 서비스 간 DB 외래 키·직접 조회는 금지다.
 */
@Component
@Profile("!prod")
public class StubAccountReader implements AccountReader {
    @Override
    public AccountState findState(UUID userId) {
        return AccountState.ACTIVE;
    }

    @Override
    public Map<UUID, String> findDisplayNames(Collection<UUID> userIds) {
        return userIds.stream()
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        this::fakeName
                ));
    }

    private String fakeName(UUID userId) {
        return "사용자-" + userId.toString().substring(0, 8);
    }
}
