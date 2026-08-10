package site.omagotchi.learningservice.gamification.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CharacterNicknameValidator {

    public static final int MAX_LENGTH = 30;

    public static String normalize(String nickname) {
        if (nickname == null) {
            throw new IllegalArgumentException("캐릭터 별명은 필수입니다.");
        }
        String normalizedNickname = nickname.trim();
        if (normalizedNickname.isEmpty() || normalizedNickname.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("캐릭터 별명은 1~30자여야 합니다.");
        }
        validatePolicy(normalizedNickname);
        return normalizedNickname;
    }

    private static void validatePolicy(String nickname) {
        // 금칙어와 이모지 정책은 nickname 정규화 흐름 안에서만 늘림
    }
}
