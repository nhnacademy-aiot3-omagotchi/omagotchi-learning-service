package site.omagotchi.learningservice.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("캐릭터 정적 asset 규칙")
class CharacterAppearanceTest {

    @Test
    @DisplayName("기본 색상은 캐릭터 폴더와 같은 파일 키를 사용한다")
    void buildsOriginalAssetKey() {
        assertEquals("night/night", CharacterAppearance.assetKey("night", "original"));
    }

    @Test
    @DisplayName("선택 색상은 색상 ID를 파일 키로 사용한다")
    void buildsColoredAssetKey() {
        assertEquals("night/pistachio", CharacterAppearance.assetKey("night", "pistachio"));
    }

    @Test
    @DisplayName("색상을 생략하면 기존 클라이언트를 위해 original을 사용한다")
    void defaultsMissingColor() {
        assertEquals("original", CharacterAppearance.normalizeColorId(null));
    }

    @Test
    @DisplayName("Frontend에 없는 색상은 거절한다")
    void rejectsUnknownColor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterAppearance.normalizeColorId("unknown")
        );
    }
}
