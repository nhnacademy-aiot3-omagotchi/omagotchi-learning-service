package site.omagotchi.learningservice.gamification.application.result;

import site.omagotchi.learningservice.gamification.domain.GameCharacter;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;

import java.util.UUID;

/**
 * 랭킹처럼 여러 사용자를 한 번에 그려야 하는 화면이 쓰는 대표 캐릭터 요약이다.
 *
 * <p>characterType은 Frontend 이미지 폴더명(game_characters.asset_key)이고
 * colorId는 그 폴더 안의 파일을 고른다. 둘 다 있어야 화면이 캐릭터를 그릴 수 있다.
 *
 * <p>대표 캐릭터가 없거나 참조하는 GameCharacter가 사라진 사용자도 랭킹에는 남아야 하므로
 * characterType과 colorId는 null을 허용한다. 화면이 기본 캐릭터로 대체한다.
 */
public record RepresentativeCharacterResult(
        UUID userId,
        Long userCharacterId,
        String displayName,
        String characterType,
        String colorId
) {

    /** 외형을 함께 조회하지 않은 호출부를 위한 생성자. 화면은 기본 캐릭터로 대체한다. */
    public RepresentativeCharacterResult(UUID userId, Long userCharacterId, String displayName) {
        this(userId, userCharacterId, displayName, null, null);
    }

    public static RepresentativeCharacterResult from(UserCharacter character, GameCharacter gameCharacter) {
        return new RepresentativeCharacterResult(
                character.getUserId(),
                character.getId(),
                character.displayName(),
                gameCharacter == null ? null : gameCharacter.getAssetKey(),
                gameCharacter == null ? null : character.getColorId()
        );
    }
}
