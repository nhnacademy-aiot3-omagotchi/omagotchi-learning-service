package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 작성자 식별자를 화면에 보여줄 이름으로 옮긴다.
 *
 * <p>표시 이름은 Gamification의 대표 캐릭터 닉네임이다. Identity Service는 아직 연결되지 않았고,
 * 닉네임은 이 서비스가 이미 소유한 값이라 외부 호출 없이 해결된다.</p>
 *
 * <p>목록은 작성자가 여러 명이므로 한 번에 조회한다. 게시글마다 부르면 N+1이 된다.</p>
 */
@Component
@RequiredArgsConstructor
public class CommunityAuthorNames {

    private final CharacterGrowthService characterGrowthService;

    /**
     * 대표 캐릭터가 없으면 null을 돌려준다. 표시용 대체 문구는 화면이 정한다.
     */
    public String of(UUID authorUserId) {
        return characterGrowthService.findRepresentativeNickname(authorUserId);
    }

    public Map<UUID, String> of(Collection<UUID> authorUserIds) {
        return characterGrowthService.findRepresentativeNicknames(authorUserIds);
    }
}
