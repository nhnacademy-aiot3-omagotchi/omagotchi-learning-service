package site.omagotchi.learningservice.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.gamification.application.port.UserCharacterQueryRepository;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private final UserCharacterQueryRepository userCharacterQueryRepository;

    /**
     * 대표 캐릭터가 없으면 null을 돌려준다. 표시용 대체 문구는 화면이 정한다.
     */
    public String of(UUID authorUserId) {
        return userCharacterQueryRepository.findRepresentativeByUserId(authorUserId)
                .map(UserCharacter::getNickname)
                .orElse(null);
    }

    public Map<UUID, String> of(Collection<UUID> authorUserIds) {
        if (authorUserIds.isEmpty()) {
            return Map.of();
        }
        return userCharacterQueryRepository.findRepresentativesByUserIds(authorUserIds).stream()
                .filter(character -> character.getNickname() != null)
                .collect(Collectors.toMap(
                        UserCharacter::getUserId,
                        UserCharacter::getNickname,
                        // 같은 사용자에 대표 캐릭터가 둘일 수 없지만, 있어도 조회가 깨지지 않게 둔다.
                        (first, second) -> first
                ));
    }
}
