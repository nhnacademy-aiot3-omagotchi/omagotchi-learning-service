package site.omagotchi.learningservice.gamification.application.port;

import site.omagotchi.learningservice.gamification.domain.UserCharacter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 대표 캐릭터 조회 경계.
 *
 * <p>"대표 캐릭터"라는 조건(is_representative)과 정렬은 저장소 기술의 관심사다.
 * Application은 무엇을 찾는지만 표현하고, 어떤 컬럼으로 거르고 어떤 순서로 가져오는지는
 * 구현이 정한다. 조회 목적이 달라지면 메서드를 나눈다.
 */
public interface UserCharacterQueryRepository {

    Optional<UserCharacter> findRepresentativeByUserId(UUID userId);

    List<UserCharacter> findRepresentativesByUserIds(Collection<UUID> userIds);

    boolean existsRepresentativeByUserId(UUID userId);

    boolean existsRepresentativeByNickname(String nickname);

    /**
     * 자기 자신을 제외하고 같은 닉네임의 대표 캐릭터가 있는지 확인한다.
     * 닉네임 변경 시 "그대로 두기"가 중복으로 잡히지 않게 한다.
     */
    boolean existsRepresentativeByNicknameExcludingId(String nickname, Long excludedUserCharacterId);

    /**
     * XP 지급처럼 같은 행을 갱신하는 작업 전에 비관적 잠금으로 조회한다.
     *
     * <p>없으면 예외를 던진다. 잠금이 필요한 시점에는 이미 존재가 확인된 뒤이므로
     * 부재는 정상 흐름이 아니라 계약 위반이다.
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         대표 캐릭터가 없으면 REPRESENTATIVE_CHARACTER_NOT_FOUND
     */
    UserCharacter getForUpdate(Long userCharacterId);
}
