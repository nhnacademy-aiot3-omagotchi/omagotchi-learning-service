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
}
