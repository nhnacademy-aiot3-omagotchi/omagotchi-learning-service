package site.omagotchi.learningservice.weather.domain;

/**
 * KMA 단기예보 하늘상태(SKY) 코드
 * 코드값: 맑음(1), 구름많음(3), 흐림(4)
 * (2는 KMA 자체에서 정의하지 않음)
 */
public enum SkyCondition {
    CLEAR, // 1: 맑음
    MOSTLY_CLOUDY, // 3: 구름많음
    CLOUDY; // 4: 흐림

    /**
     * KMA 응답의 SKY 코드 문자열을 SkyCondition으로 변환
     *
     * @param code KMA가 내려준 SKY 코드(1, 3, 4 중 하나)
     * @return 매핑된 하늘 상태
     * @throws IllegalArgumentException 알려지지 않은 코드가 들어온 경우
     */
    public static SkyCondition fromCode(String code) {
        return switch (code) {
            case "1" -> CLEAR;
            case "3" -> MOSTLY_CLOUDY;
            case "4" -> CLOUDY;
            default -> throw new IllegalArgumentException("알 수 없는 하늘상태 코드: " + code);
        };
    }
}
