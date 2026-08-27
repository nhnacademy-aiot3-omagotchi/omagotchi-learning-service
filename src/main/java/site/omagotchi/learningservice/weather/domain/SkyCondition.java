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
}
