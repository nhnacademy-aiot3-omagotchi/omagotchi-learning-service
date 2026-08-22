package site.omagotchi.learningservice.sensor.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;

import java.time.Instant;
import java.util.List;

public record SpaceSeriesResponse(
        String location,
        String measurement,
        SeriesWindow window,
        String interval,
        Instant from,
        Instant to,
        Instant serverTime,
        Sources sources,
        int sensorCount,
        List<SensorResponse> sensors,
        List<PointResponse> points
) {

    /** 어느 버킷에서 읽었는지. 화면에 표시하지 않고 확인용으로만 쓴다. */
    public record Sources(String settled, String hot) {
    }

    /** 센서 명단. point는 InfluxDB 태그, displayName은 기기 마스터에서 온다. */
    public record SensorResponse(String deviceEui, String point, String displayName) {
    }

    /** partial은 진행 중인 마지막 점에만 붙는다. */
    public record PointResponse(
            Instant time,
            Double avg,
            Double min,
            String minDeviceEui,
            Double max,
            String maxDeviceEui,
            int count,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean partial
    ) {
    }

    public static SpaceSeriesResponse from(SpaceSeries series) {
        List<SensorResponse> sensors = series.sensors().stream()
                .map(sensor -> new SensorResponse(
                        sensor.deviceEui(), sensor.point(), sensor.displayName()))
                .toList();

        List<PointResponse> points = series.points().stream()
                .map(point -> new PointResponse(
                        point.time(),
                        point.avg(),
                        point.min(),
                        point.minDeviceEui(),
                        point.max(),
                        point.maxDeviceEui(),
                        point.count(),
                        point.partial() ? Boolean.TRUE : null))
                .toList();

        return new SpaceSeriesResponse(
                series.location(),
                series.measurement(),
                series.window(),
                series.window().fluxInterval(),
                series.from(),
                series.to(),
                series.to(),
                new Sources(
                        series.window().settledBucket().name(),
                        series.window().hotBucket().name()),
                sensors.size(),
                sensors,
                points
        );
    }
}