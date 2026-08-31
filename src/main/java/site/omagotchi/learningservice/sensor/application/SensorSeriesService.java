package site.omagotchi.learningservice.sensor.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.port.SpaceSeriesRepository;
import site.omagotchi.learningservice.sensor.application.query.SpaceSeriesQuery;
import site.omagotchi.learningservice.sensor.application.result.SensorRef;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SensorSeriesService {

    private final SensorDeviceRepository sensorDeviceRepository;
    private final SpaceSeriesRepository spaceSeriesRepository;
    private final CohortAccessService cohortAccessService;
    private final SpaceCohortQueryService spaceCohortQueryService;
    private final SensorSeriesProperties properties;
    private final Clock clock;

    /**
     * 공간 평균 시계열. <b>이 Feature에서 소속이면 되는 유일한 경로다.</b>
     *
     * <p>학생이 보는 것은 "지금 이 공간의 공기가 어떤가"이지 어떤 기기가 몇 대 있는지가
     * 아니다. 응답은 공간 단위 평균이고 기기 마스터·임계치는 담기지 않는다. 그 조건이
     * 깨지면 인가 기준도 같이 매니저로 올려야 한다.</p>
     */
    public SpaceSeries getSpaceSeries(
            Long cohortId,
            UUID requesterId,
            String location,
            String measurement,
            String window
    ) {
        cohortAccessService.requireActiveMembershipId(cohortId, requesterId);
        SeriesWindow seriesWindow = toWindow(window);

        Instant now = clock.instant();
        Instant from = seriesWindow.from(now);
        Instant boundary = seriesWindow.settledUntil(now, properties.zone());

        // 운영 중인 기기만 집계한다. 미등록·비활성 기기는 평균에 섞이지 않는다
        List<Long> spaceIds = spaceCohortQueryService.findSpaceIdsByCohortId(cohortId);
        List<SensorDevice> activeDevices = sensorDeviceRepository.findActiveBySpaceIds(spaceIds);
        Set<String> activeEuis = activeDevices.stream()
                .map(SensorDevice::getDeviceEui)
                .collect(Collectors.toSet());

        SpaceSeries series = spaceSeriesRepository.findSpaceSeries(
                new SpaceSeriesQuery(location, measurement, seriesWindow,
                        from, boundary, now, properties.zone(), activeEuis));

        return withDisplayNames(series, activeDevices);
    }

    /** 저장소는 표시명을 모른다. 기기 마스터에서 찾아 채운다. */
    private SpaceSeries withDisplayNames(SpaceSeries series, List<SensorDevice> devices) {
        Map<String, String> displayNames = devices.stream()
                .filter(device -> Objects.nonNull(device.getDisplayName()))
                .collect(Collectors.toMap(
                        SensorDevice::getDeviceEui,
                        SensorDevice::getDisplayName
                ));

        List<SensorRef> named = series.sensors().stream()
                .map(sensor -> sensor.withDisplayName(
                        displayNames.get(sensor.deviceEui())
                ))
                .toList();

        return new SpaceSeries(
                series.location(),
                series.measurement(),
                series.window(),
                series.from(),
                series.to(),
                named,
                series.points()
        );
    }

    /* 요청 문자열을 조회 창으로 바꾼다. DAY·WEEK·MONTH가 아니면 400. */
    private SeriesWindow toWindow(String window) {
        try {
            return SeriesWindow.valueOf(window.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, exception);
        }
    }
}
