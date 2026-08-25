package site.omagotchi.learningservice.weather.domain;

/**
 * KMA 단기예보 강수형태(PTY) 코드
 * 코드값: 없음(0), 비(1), 비/눈(2), 눈(3), 소나기(4) - 단기예보 기준
 * 초단기예보(getUltraSrtFcst)는 5(빗방울)~7(눈날림)까지 코드가 더 있으나 이 서비스는 단기예보(getVilageFcst)만 쓰므로 다루지 않음
 */
public enum PrecipitationType {
    NONE, // 0: 없음
    RAIN, // 1: 비
    RAIN_SNOW, // 2: 비/눈
    SNOW, // 3: 눈
    SHOWER; // 4: 소나기 (단기예보 기준)

    /**
     * KMA 응답의 PTY 코드 문자열을 PrecipitationType으로 변환
     *
     * @param code KMA가 내려준 PTY 코드 (0 ~ 4 중 하나)
     * @return 매핑된 강수형태
     * @throws IllegalArgumentException 알려지지 않은 코드가 들어온 경우
     */
    public static PrecipitationType fromCode(String code) {
        return switch (code) {
            case "0" -> NONE;
            case "1" -> RAIN;
            case "2" -> RAIN_SNOW;
            case "3" -> SNOW;
            case "4" -> SHOWER;
            default -> throw new IllegalArgumentException("알 수 없는 강수형태 코드: " + code);
        };
    }
}
