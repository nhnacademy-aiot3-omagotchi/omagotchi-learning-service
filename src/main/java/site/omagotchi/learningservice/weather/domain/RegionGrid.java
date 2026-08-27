package site.omagotchi.learningservice.weather.domain;

public record RegionGrid(
        String sido, // 시도
        String sigungu, // 시군구
        String eupmyeondong, // 구 단위 대표행이면 빈 문자열
        int nx,
        int ny
) {
}
