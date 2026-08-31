package site.omagotchi.learningservice.weather.infrastructure;

import java.util.List;

/**
 * KMA 단기예보 응답 DTO
 */
public record KmaForecastResponse(
        Response response
) {

    public record Response(
            Header header,
            Body body
    ) {
    }

    public record Header(
            String resultCode,
            String resultMsg
    ) {
    }

    public record Body(
            Items items,
            int numOfRows,
            int pageNo,
            int totalCount
    ) {
    }

    public record Items(
            List<Item> item
    ) {
    }

    public record Item(
            String baseDate, // 발표일자 (YYYYMMDD)
            String baseTime, // 발표시각 (HHmm)
            String category, // 자료구분코드 (TMP, SKY, PTY, POP, REH, WSD 등)
            String fcstDate, // 예보일자 (YYYYMMDD)
            String fcstTime, // 예보시각 (HHmm)
            String fcstValue, // 예보 값 - 카테고리마다 값 형식이 다름(숫자 문자열 또는 "1mm 미만" 같은 텍스트) (그래서 String으로 잡음)
            int nx, // 예보지점 X 격자좌표
            int ny // 예보지점 Y 격자좌표
    ) {
    }
}
