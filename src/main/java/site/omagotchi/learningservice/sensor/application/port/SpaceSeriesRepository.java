package site.omagotchi.learningservice.sensor.application.port;

import site.omagotchi.learningservice.sensor.application.query.SpaceEnvironmentQuery;
import site.omagotchi.learningservice.sensor.application.query.SpaceSeriesQuery;
import site.omagotchi.learningservice.sensor.application.result.SensorReadingSnapshot;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;

import java.util.List;

public interface SpaceSeriesRepository {

    /** 공간 안의 모든 센서를 시간대별로 묶어 평균·최소·최대를 낸다.
     *  돌려주는 SensorRef의 displayName은 비어 있다. 표시명은 서비스가 채운다. */
    SpaceSeries findSpaceSeries(SpaceSeriesQuery query);

    /** 기기별·항목별 마지막 값. 조회 구간 안에 값이 없는 기기는 빠진다.
     *  공간 묶음은 서비스가 한다 — 시계열에는 공간을 가리키는 태그가 없다. */
    List<SensorReadingSnapshot> findLatestReadings(SpaceEnvironmentQuery query);
}