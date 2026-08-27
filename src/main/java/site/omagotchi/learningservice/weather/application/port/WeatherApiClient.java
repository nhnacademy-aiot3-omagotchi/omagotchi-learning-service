package site.omagotchi.learningservice.weather.application.port;

import site.omagotchi.learningservice.weather.domain.WeatherForecast;

import java.util.List;

/**
 * 특정 격자좌표에 대한 예보 조회 포트
 * 구현체(KMA 등)가 무엇인지, base_date/base_time을 어떻게 계산하는지는 이 인터페이스를 쓰는 쪽(application)이 알 필요 X
 */
public interface WeatherApiClient {

    /**
     * 주어진 격자좌표의 예보를 조회한다
     *
     * @param nx 예보지점 X 격자좌표
     * @param ny 예보지점 Y 격자좌표
     * @return 조회 가능한 전체 기간의 예보 목록
     */
    List<WeatherForecast> getForecast(int nx, int ny);
}
