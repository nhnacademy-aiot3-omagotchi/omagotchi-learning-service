package site.omagotchi.learningservice.gamification.domain;

import java.util.Set;

/**
 * Frontend 정적 캐릭터 asset 규칙과 사용자 색상 선택을 표현한다.
 */
public final class CharacterAppearance {

    public static final String DEFAULT_COLOR_ID = "original";

    private static final Set<String> ALLOWED_COLOR_IDS = Set.of(
            DEFAULT_COLOR_ID,
            "pistachio",
            "cyan",
            "cream_can",
            "light_coral",
            "light_purple",
            "white",
            "dark_gray"
    );

    private CharacterAppearance() {
    }

    public static String normalizeColorId(String colorId) {
        String normalized = colorId == null || colorId.isBlank()
                ? DEFAULT_COLOR_ID
                : colorId.trim().toLowerCase();
        if (!ALLOWED_COLOR_IDS.contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 캐릭터 색상입니다.");
        }
        return normalized;
    }

    public static String assetKey(String characterAssetKey, String colorId) {
        String normalizedColorId = normalizeColorId(colorId);
        String fileKey = DEFAULT_COLOR_ID.equals(normalizedColorId)
                ? characterAssetKey
                : normalizedColorId;
        return characterAssetKey + "/" + fileKey;
    }
}
