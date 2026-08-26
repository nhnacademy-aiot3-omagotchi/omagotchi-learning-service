package site.omagotchi.learningservice.sensor.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.rule.application.SensorDeviceService;
import site.omagotchi.learningservice.sensor.application.port.SensorSeriesRepository;
import site.omagotchi.learningservice.sensor.application.result.SensorSeries;
import site.omagotchi.learningservice.sensor.domain.SeriesPoint;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("센서 시계열 서비스")
class SensorSeriesServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:30:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String DEVICE_EUI = "0011223344556677";

    @Mock
    private SensorSeriesRepository seriesRepository;

    @Mock
    private SensorDeviceService sensorDeviceService;

    private SensorSeriesService service;

    @BeforeEach
    void setUp() {
        SensorSeriesProperties properties = new SensorSeriesProperties(SEOUL);
        Clock fixedClock = Clock.fixed(NOW, SEOUL);
        service = new SensorSeriesService(seriesRepository, sensorDeviceService, properties, fixedClock);
    }

    @Test
    @DisplayName("저장소가 준 점들과 기기 표시명, 조회 범위를 묶어 돌려준다")
    void assemblesSeriesFromRepositoryAndDeviceMaster() {
        // given
        List<SeriesPoint> points = List.of(
                new SeriesPoint(Instant.parse("2026-08-25T09:00:00Z"), 23.5, false));
        when(seriesRepository.findSeries(any())).thenReturn(points);
        when(sensorDeviceService.findDisplayName(DEVICE_EUI)).thenReturn(Optional.of("강의실 온도계"));

        // when
        SensorSeries result = service.getSeries(DEVICE_EUI, "temperature", "day");

        // then
        assertEquals(DEVICE_EUI, result.deviceEui());
        assertEquals("강의실 온도계", result.deviceDisplayName());
        assertEquals("temperature", result.measurement());
        assertEquals(SeriesWindow.DAY, result.window());
        assertEquals(NOW.minus(Duration.ofDays(1)), result.from());
        assertEquals(NOW, result.to());
        assertEquals(points, result.points());
    }

    @Test
    @DisplayName("기기 마스터에 표시명이 없으면 null로 둔다")
    void leavesDisplayNameNullWhenUnknownDevice() {
        // given
        when(seriesRepository.findSeries(any())).thenReturn(List.of());
        when(sensorDeviceService.findDisplayName(DEVICE_EUI)).thenReturn(Optional.empty());

        // when
        SensorSeries result = service.getSeries(DEVICE_EUI, "temperature", "WEEK");

        // then
        assertNull(result.deviceDisplayName());
        assertEquals(SeriesWindow.WEEK, result.window());
    }

    @Test
    @DisplayName("지원하지 않는 window 문자열이면 BusinessException이 발생한다")
    void rejectsUnknownWindow() {
        try {
            service.getSeries(DEVICE_EUI, "temperature", "YEAR");
            fail("예외가 발생해야 하는데 발생하지 않았다");
        } catch (BusinessException exception) {
            // 예외가 터져서 여기로 들어오면 통과
        }
    }
}