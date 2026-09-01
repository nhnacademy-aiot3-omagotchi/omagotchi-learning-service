package site.omagotchi.learningservice.sensor.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.port.SpaceSeriesRepository;
import site.omagotchi.learningservice.sensor.application.query.SpaceSeriesQuery;
import site.omagotchi.learningservice.sensor.application.result.SensorRef;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.sensor.domain.SpaceSeriesPoint;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("센서 시계열 서비스")
class SensorSeriesServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:30:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String DEVICE_EUI = "0011223344556677";
    private static final Long COHORT_ID = 1L;
    private static final Long SPACE_ID = 10L;
    private static final UUID REQUESTER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );

    @Mock
    private SensorDeviceRepository sensorDeviceRepository;

    @Mock
    private SpaceSeriesRepository spaceSeriesRepository;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private SpaceCohortQueryService spaceCohortQueryService;

    private SensorSeriesService service;

    @BeforeEach
    void setUp() {
        SensorSeriesProperties properties = new SensorSeriesProperties(SEOUL);
        Clock fixedClock = Clock.fixed(NOW, SEOUL);
        service = new SensorSeriesService(
                sensorDeviceRepository,
                spaceSeriesRepository,
                cohortAccessService,
                spaceCohortQueryService,
                properties,
                fixedClock
        );
    }

    @Test
    @DisplayName("공간 시계열은 활성 기기만 조회하고 기기 마스터의 표시명을 채운다")
    void assemblesSpaceSeriesFromActiveDevices() {
        // given: 시계열 저장소에는 기기 마스터에 존재하는 센서와 없는 센서가 함께 있다
        List<SensorRef> sensors = List.of(
                new SensorRef(DEVICE_EUI, "window-side", null),
                new SensorRef("8899aabbccddeeff", "door-side", null));
        List<SpaceSeriesPoint> points = List.of(
                SpaceSeriesPoint.empty(Instant.parse("2026-08-25T09:00:00Z"), false));
        SpaceSeries fromRepository = new SpaceSeries(
                "A강의실",
                "co2",
                SeriesWindow.DAY,
                NOW.minus(Duration.ofDays(1)),
                NOW,
                sensors,
                points
        );
        SensorDevice activeDevice = SensorDevice.create(
                DEVICE_EUI, SPACE_ID, "CO2-01", "창가 CO2 센서", "window-side", 60, NOW);

        when(spaceCohortQueryService.findSpaceIdsByCohortId(COHORT_ID))
                .thenReturn(List.of(SPACE_ID));
        when(sensorDeviceRepository.findActiveBySpaceIds(List.of(SPACE_ID)))
                .thenReturn(List.of(activeDevice));
        when(spaceSeriesRepository.findSpaceSeries(any())).thenReturn(fromRepository);

        // when
        SpaceSeries result = service.getSpaceSeries(
                COHORT_ID,
                REQUESTER_ID,
                "A강의실",
                "co2",
                "day"
        );

        // then
        assertEquals("A강의실", result.location());
        assertEquals("co2", result.measurement());
        assertEquals(SeriesWindow.DAY, result.window());
        assertEquals(points, result.points());
        assertEquals(2, result.sensors().size());
        assertEquals("창가 CO2 센서", result.sensors().get(0).displayName());
        assertNull(result.sensors().get(1).displayName());

        ArgumentCaptor<SpaceSeriesQuery> queryCaptor = ArgumentCaptor.forClass(SpaceSeriesQuery.class);
        verify(spaceSeriesRepository).findSpaceSeries(queryCaptor.capture());
        assertEquals(Set.of(DEVICE_EUI), queryCaptor.getValue().includedDeviceEuis());
    }

    @Test
    @DisplayName("지원하지 않는 window 문자열이면 BusinessException이 발생한다")
    void rejectsUnknownWindow() {
        assertThrows(BusinessException.class, () -> service.getSpaceSeries(
                COHORT_ID,
                REQUESTER_ID,
                "A강의실",
                "co2",
                "YEAR"
        ));
    }
}
