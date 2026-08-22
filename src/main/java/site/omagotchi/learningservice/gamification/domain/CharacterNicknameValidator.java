package site.omagotchi.learningservice.gamification.domain;

import com.vane.badwordfiltering.BadWordFiltering;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CharacterNicknameValidator {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 12;

    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile(
            "^[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]+$"
    );
    private static final BadWordFiltering BAD_WORD_FILTERING = new BadWordFiltering();
    private static final Set<String> RESERVED_NICKNAMES = Set.of(
            "admin", "administrator", "system", "manager",
            "관리자", "운영자", "영자", "공지", "마스터", "시스템"
    );
    private static final Set<String> ADDITIONAL_FORBIDDEN_FRAGMENTS = Set.of(
            "야스", "shit", "cunt", "dick", "porn", "motherfucker"
    );

    public static String normalize(String nickname) {
        if (nickname == null) {
            throw new IllegalArgumentException("닉네임은 필수입니다.");
        }
        String normalizedNickname = Normalizer
                .normalize(nickname, Normalizer.Form.NFKC)
                .trim();
        if (normalizedNickname.length() < MIN_LENGTH
                || normalizedNickname.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("닉네임은 2~12자여야 합니다.");
        }
        validatePolicy(normalizedNickname);
        return normalizedNickname;
    }

    private static void validatePolicy(String nickname) {
        if (!ALLOWED_CHARACTERS.matcher(nickname).matches()) {
            throw new IllegalArgumentException("닉네임에는 한글, 영문, 숫자만 사용할 수 있습니다.");
        }

        String lowercase = nickname.toLowerCase(Locale.ROOT);
        String withoutDigits = collapseRepeated(lowercase.replaceAll("[0-9]", ""));
        String phoneticNormalized = withoutDigits
                .replace("시이", "시")
                .replace("씨이", "씨")
                .replace("쉬이", "쉬");
        String leetNormalized = collapseRepeated(lowercase
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('7', 't'));

        if (RESERVED_NICKNAMES.stream().anyMatch(lowercase::contains)
                || containsForbiddenFragment(lowercase)
                || containsForbiddenFragment(withoutDigits)
                || containsForbiddenFragment(phoneticNormalized)
                || containsForbiddenFragment(leetNormalized)) {
            throw new IllegalArgumentException("사용할 수 없는 닉네임입니다.");
        }
    }

    private static boolean containsForbiddenFragment(String nickname) {
        return BAD_WORD_FILTERING.check(nickname)
                || ADDITIONAL_FORBIDDEN_FRAGMENTS.stream().anyMatch(nickname::contains);
    }

    private static String collapseRepeated(String value) {
        return value.replaceAll("(.)\\1+", "$1");
    }
}
