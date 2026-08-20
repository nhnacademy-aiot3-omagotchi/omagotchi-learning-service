package site.omagotchi.learningservice.sensor.application.port;

import site.omagotchi.learningservice.sensor.application.query.SensorSeriesQuery;
import site.omagotchi.learningservice.sensor.domain.SeriesPoint;

import java.util.List;

public interface SensorSeriesRepository {
    /** 확정 구간과 진행 중 구간을 합쳐 시간순으로 돌려준다. */
    List<SeriesPoint> findSeries(SensorSeriesQuery query);
}
