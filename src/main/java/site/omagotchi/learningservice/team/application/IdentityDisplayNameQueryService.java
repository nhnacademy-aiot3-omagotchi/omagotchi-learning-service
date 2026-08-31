package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** 다른 Feature에 Identity 계정 표시명을 제공하는 team Feature의 공개 조회 계약. */
@Service
@RequiredArgsConstructor
public class IdentityDisplayNameQueryService {

    private final IdentityAccountClient identityAccountClient;

    public Map<UUID, String> findDisplayNames(Collection<UUID> userIds) {
        return identityAccountClient.findDisplayNames(userIds);
    }
}
