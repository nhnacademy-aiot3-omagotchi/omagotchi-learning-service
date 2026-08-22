package site.omagotchi.learningservice.sensor.application.port;

import site.omagotchi.learningservice.sensor.application.query.SpaceSeriesQuery;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;

public interface SpaceSeriesRepository {

    /** 공간 안의 모든 센서를 시간대별로 묶어 평균·최소·최대를 낸다.
     *  돌려주는 SensorRef의 displayName은 비어 있다. 표시명은 서비스가 채운다. */
    SpaceSeries findSpaceSeries(SpaceSeriesQuery query);
}