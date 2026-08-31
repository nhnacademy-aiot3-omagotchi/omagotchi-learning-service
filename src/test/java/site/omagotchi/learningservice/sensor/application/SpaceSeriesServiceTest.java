package site.omagotchi.learningservice.sensor.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.port.SpaceSeriesRepository;
import site.omagotchi.learningservice.sensor.application.result.SensorRef;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.sensor.domain.SpaceSeriesPoint;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("공간 시계열 서비스")
class SpaceSeriesServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:30:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private SpaceSeriesRepository spaceSeriesRepository;

    @Mock
    private SensorDeviceService sensorDeviceService;

    private SpaceSeriesService service;

    @BeforeEach
    void setUp() {
        SensorSeriesProperties properties = new SensorSeriesProperties(SEOUL);
        Clock fixedClock = Clock.fixed(NOW, SEOUL);
        service = new SpaceSeriesService(spaceSeriesRepository, sensorDeviceService, properties, fixedClock);
    }

    @Test
    @DisplayName("기기 마스터에 있는 센서는 표시명이 채워지고 없는 센서는 null로 남는다")
    void fillsDisplayNamesFromDeviceMaster() {
        // given: 저장소는 표시명 없는 센서 두 개를 돌려준다
        List<SensorRef> sensors = List.of(
                new SensorRef("0011223344556677", "window-side", null),
                new SensorRef("8899aabbccddeeff", "door-side", null));
        List<SpaceSeriesPoint> points = List.of(
                SpaceSeriesPoint.empty(Instant.parse("2026-08-25T09:00:00Z"), false));
        SpaceSeries fromRepository = new SpaceSeries("A강의실", "co2", SeriesWindow.DAY,
                NOW.minus(java.time.Duration.ofDays(1)), NOW, sensors, points);

        when(sensorDeviceService.findActiveDeviceEuis()).thenReturn(Set.of("0011223344556677"));
        when(spaceSeriesRepository.findSpaceSeries(any())).thenReturn(fromRepository);
        // 기기 마스터는 첫 번째 기기의 이름만 안다
        when(sensorDeviceService.findDisplayNames())
                .thenReturn(Map.of("0011223344556677", "창가 CO2 센서"));

        // when
        SpaceSeries result = service.getSpaceSeries("A강의실", "co2", "day");

        // then
        assertEquals("A강의실", result.location());
        assertEquals("co2", result.measurement());
        assertEquals(SeriesWindow.DAY, result.window());
        assertEquals(points, result.points());

        assertEquals(2, result.sensors().size());
        assertEquals("창가 CO2 센서", result.sensors().get(0).displayName());
        assertNull(result.sensors().get(1).displayName());
    }

    @Test
    @DisplayName("지원하지 않는 window 문자열이면 BusinessException이 발생한다")
    void rejectsUnknownWindow() {
        try {
            service.getSpaceSeries("A강의실", "co2", "YEAR");
            fail("예외가 발생해야 하는데 발생하지 않았다");
        } catch (BusinessException exception) {
            // 예외가 터져서 여기로 들어오면 통과
        }
    }
}