package site.omagotchi.learningservice.environment.application.query;

import java.util.List;

/**
 * 센서 이벤트 조회 결과 페이지
 *
 * @param items 이벤트들. 수신 시각 내림차순
 * @param page 이 페이지 페이지 번호
 * @param size
 * @param totalElements
 * @param totalPages
 */
public record SensorEventPage (
        List<SensorEventItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages
){
    public SensorEventPage{
        items = List.copyOf(items);
    }
}
