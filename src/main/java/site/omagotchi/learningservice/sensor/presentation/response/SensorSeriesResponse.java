package site.omagotchi.learningservice.sensor.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import site.omagotchi.learningservice.sensor.domain.SensorSeries;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;

import java.time.Instant;
import java.util.List;

public record SensorSeriesResponse(
        String deviceEui,
        String deviceDisplayName,
        String measurement,
        SeriesWindow window,
        String interval,
        Instant from,
        Instant to,
        Instant serverTime,
        Sources sources,
        List<PointResponse> points
) {

    /** 어느 버킷에서 읽었는지. 화면에 표시하지 않고 확인용으로만 쓴다. */
    public record Sources(String settled, String hot) {
    }

    /** partial은 진행 중인 마지막 점에만 붙는다. 확정된 점에는 필드 자체가 없다. */
    public record PointResponse(Instant time, Double value, @JsonInclude(JsonInclude.Include.NON_NULL) Boolean partial) {
    }

    public static SensorSeriesResponse from(SensorSeries series) {
        List<PointResponse> points = series.points().stream()
                .map(point -> new PointResponse(
                        point.time(),
                        point.value(),
                        point.partial() ? Boolean.TRUE : null))
                .toList();

        return new SensorSeriesResponse(
                series.deviceEui(),
                series.deviceDisplayName(),
                series.measurement(),
                series.window(),
                series.window().fluxInterval(),
                series.from(),
                series.to(),
                series.to(),
                new Sources(
                        series.window().settledBucket().name(),
                        series.window().hotBucket().name()),
                points
        );
    }
}