package site.omagotchi.learningservice.sensor.infrastructure;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.query.SpaceEnvironmentQuery;
import site.omagotchi.learningservice.sensor.application.query.SpaceSeriesQuery;
import site.omagotchi.learningservice.sensor.application.result.SensorReadingSnapshot;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.sensor.domain.SpaceSeriesPoint;
import site.omagotchi.learningservice.sensor.infrastructure.influx.InfluxSpaceSeriesRepository;
import site.omagotchi.learningservice.sensor.infrastructure.influx.SensorInfluxProperties;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Influx 공간 시계열 저장소")
class InfluxSpaceSeriesRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:30:00Z");
    private static final Instant BOUNDARY = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant FROM = Instant.parse("2026-08-24T10:30:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final String DEVICE_A = "0011223344556677";
    private static final String DEVICE_B = "8899aabbccddeeff";

    @Mock
    private InfluxDBClient client;

    @Mock
    private QueryApi queryApi;

    private InfluxSpaceSeriesRepository repository;

    @BeforeEach
    void setUp() {
        SensorInfluxProperties properties = new SensorInfluxProperties(
                "http://localhost:8086", "test-token", "test-org",
                new SensorInfluxProperties.Buckets("raw-bucket", "avg1h-bucket", "avg1d-bucket"));
        repository = new InfluxSpaceSeriesRepository(client, properties);
    }

    /** 조회 조건을 만든다. 포함 기기 목록만 테스트마다 다르다. */
    private SpaceSeriesQuery query(Set<String> includedDeviceEuis) {
        return new SpaceSeriesQuery("A강의실", "co2", SeriesWindow.DAY,
                FROM, BOUNDARY, NOW, SEOUL, includedDeviceEuis);
    }

    /** InfluxDB 레코드 하나를 꾸민다. */
    private FluxRecord record(Instant time, String deviceEui, String point, Object value) {
        FluxRecord fluxRecord = new FluxRecord(0);
        fluxRecord.getValues().put("_time", time);
        fluxRecord.getValues().put("device_eui", deviceEui);
        fluxRecord.getValues().put("point", point);
        fluxRecord.getValues().put("_value", value);
        return fluxRecord;
    }

    /** 레코드들을 테이블 하나에 담아 조회 결과 형태로 만든다. */
    private List<FluxTable> tableOf(FluxRecord... records) {
        FluxTable table = new FluxTable();
        table.getRecords().addAll(new ArrayList<>(List.of(records)));
        return List.of(table);
    }

    @Test
    @DisplayName("같은 시각의 센서 값들을 평균·최소·최대로 집계한다")
    void aggregatesValuesAtSameTime() {
        // given: 확정 구간에 같은 시각의 두 센서 값, 진행 중 구간에 한 센서 값
        Instant settledTime = Instant.parse("2026-08-25T09:00:00Z");
        Instant hotTime = Instant.parse("2026-08-25T10:00:00Z");

        when(client.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), anyString())).thenReturn(
                tableOf(
                        record(settledTime, DEVICE_A, "window-side", 400.0),
                        record(settledTime, DEVICE_B, "door-side", 500.0)),
                tableOf(
                        record(hotTime, DEVICE_A, "window-side", 480.0)));

        // when
        SpaceSeries series = repository.findSpaceSeries(query(Set.of(DEVICE_A, DEVICE_B)));

        // then: 확정 구간의 점 — 두 값이 하나의 점으로 집계된다
        assertEquals(2, series.points().size());
        SpaceSeriesPoint settled = series.points().get(0);
        assertEquals(settledTime, settled.time());
        assertEquals(450.0, settled.avg());
        assertEquals(400.0, settled.min());
        assertEquals(DEVICE_A, settled.minDeviceEui());
        assertEquals(500.0, settled.max());
        assertEquals(DEVICE_B, settled.maxDeviceEui());
        assertEquals(2, settled.count());
        assertFalse(settled.partial());

        // 진행 중 구간의 점 — partial이 붙는다
        SpaceSeriesPoint hot = series.points().get(1);
        assertEquals(480.0, hot.avg());
        assertEquals(1, hot.count());
        assertTrue(hot.partial());

        // 센서 명단은 레코드의 태그에서 모인다. 표시명은 아직 없다
        assertEquals(2, series.sensors().size());
        assertEquals(DEVICE_A, series.sensors().get(0).deviceEui());
        assertEquals("window-side", series.sensors().get(0).point());
        assertNull(series.sensors().get(0).displayName());

        // 조회는 확정·진행 중 두 번 일어난다
        verify(queryApi, times(2)).query(anyString(), anyString());
    }

    @Test
    @DisplayName("포함 목록에 없는 기기의 레코드는 버린다")
    void skipsRecordsFromExcludedDevices() {
        // given: 확정 구간에 포함 기기 하나, 미포함 기기 하나. 진행 중 구간은 빈 결과
        when(client.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), anyString())).thenReturn(
                tableOf(
                        record(Instant.parse("2026-08-25T08:00:00Z"), DEVICE_A, "window-side", 410.0),
                        record(Instant.parse("2026-08-25T09:00:00Z"), DEVICE_B, "door-side", 999.0)),
                List.of());

        // when: DEVICE_A만 운영 중이다
        SpaceSeries series = repository.findSpaceSeries(query(Set.of(DEVICE_A)));

        // then: 미포함 기기의 시각은 점 자체가 만들어지지 않는다
        assertEquals(1, series.points().size());
        assertEquals(410.0, series.points().get(0).avg());

        // 센서 명단에도 미포함 기기는 없다
        assertEquals(1, series.sensors().size());
        assertEquals(DEVICE_A, series.sensors().get(0).deviceEui());
    }

    @Test
    @DisplayName("값이 없는 시각은 count 0의 빈 점이 된다")
    void producesEmptyPointWhenValueMissing() {
        // given: 확정 구간에 값이 null인 레코드(빈 시간대). 진행 중 구간은 빈 결과
        Instant emptyTime = Instant.parse("2026-08-25T07:00:00Z");
        when(client.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), anyString())).thenReturn(
                tableOf(record(emptyTime, DEVICE_A, "window-side", null)),
                List.of());

        // when
        SpaceSeries series = repository.findSpaceSeries(query(Set.of(DEVICE_A)));

        // then
        assertEquals(1, series.points().size());
        SpaceSeriesPoint point = series.points().get(0);
        assertEquals(emptyTime, point.time());
        assertNull(point.avg());
        assertEquals(0, point.count());
    }

    @Test
    @DisplayName("운영 중인 기기가 하나도 없으면 InfluxDB에 묻지 않고 빈 결과를 준다")
    void returnsEmptySeriesWithoutQueryingWhenNoActiveDevices() {
        // when
        SpaceSeries series = repository.findSpaceSeries(query(Set.of()));

        // then
        assertEquals("A강의실", series.location());
        assertEquals(0, series.sensors().size());
        assertEquals(0, series.points().size());
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("location 형식이 틀리면 InfluxDB에 묻지 않고 예외를 던진다")
    void rejectsInvalidLocationBeforeQuerying() {
        SpaceSeriesQuery badQuery = new SpaceSeriesQuery("bad\"location", "co2", SeriesWindow.DAY,
                FROM, BOUNDARY, NOW, SEOUL, Set.of(DEVICE_A));

        try {
            repository.findSpaceSeries(badQuery);
            fail("예외가 발생해야 하는데 발생하지 않았다");
        } catch (BusinessException exception) {
            verifyNoInteractions(client);
        }
    }

    @Test
    @DisplayName("measurement 형식이 틀리면 InfluxDB에 묻지 않고 예외를 던진다")
    void rejectsInvalidMeasurementBeforeQuerying() {
        SpaceSeriesQuery badQuery = new SpaceSeriesQuery("A강의실", "bad measurement!", SeriesWindow.DAY,
                FROM, BOUNDARY, NOW, SEOUL, Set.of(DEVICE_A));

        try {
            repository.findSpaceSeries(badQuery);
            fail("예외가 발생해야 하는데 발생하지 않았다");
        } catch (BusinessException exception) {
            verifyNoInteractions(client);
        }
    }

    /** 현재 환경 조회 조건. 기기 목록만 테스트마다 다르다. */
    private SpaceEnvironmentQuery latestQuery(Set<String> deviceEuis) {
        return new SpaceEnvironmentQuery(
                deviceEuis, List.of("co2", "temperature", "humidity"), FROM, NOW);
    }

    /** 현재 환경 조회 결과 레코드. 시계열과 달리 _measurement 를 함께 읽는다. */
    private FluxRecord latestRecord(Instant time, String deviceEui, String measurement, Object value) {
        FluxRecord fluxRecord = new FluxRecord(0);
        fluxRecord.getValues().put("_time", time);
        fluxRecord.getValues().put("device_eui", deviceEui);
        fluxRecord.getValues().put("_measurement", measurement);
        fluxRecord.getValues().put("_value", value);
        return fluxRecord;
    }

    @Test
    @DisplayName("기기별·항목별 마지막 값을 한 번의 조회로 읽는다")
    void readsLatestReadingsInOneQuery() {
        // given
        Instant time = Instant.parse("2026-08-25T10:20:00Z");
        when(client.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), anyString())).thenReturn(tableOf(
                latestRecord(time, DEVICE_A, "co2", 612.0),
                latestRecord(time, DEVICE_B, "temperature", 23.4)));

        // when
        List<SensorReadingSnapshot> readings =
                repository.findLatestReadings(latestQuery(Set.of(DEVICE_A, DEVICE_B)));

        // then
        assertEquals(2, readings.size());
        assertEquals(DEVICE_A, readings.get(0).deviceEui());
        assertEquals("co2", readings.get(0).measurement());
        assertEquals(612.0, readings.get(0).value());
        assertEquals(time, readings.get(0).time());

        // 공간이 몇 곳이든 조회는 한 번이다
        verify(queryApi, times(1)).query(anyString(), anyString());

        ArgumentCaptor<String> fluxCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryApi).query(fluxCaptor.capture(), anyString());
        String flux = fluxCaptor.getValue();
        assertTrue(flux.contains("raw-bucket"));
        assertTrue(flux.contains(DEVICE_A));
        assertTrue(flux.contains("last()"));
    }

    @Test
    @DisplayName("값이 숫자가 아닌 레코드는 버린다")
    void skipsNonNumericLatestRecords() {
        when(client.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), anyString())).thenReturn(tableOf(
                latestRecord(Instant.parse("2026-08-25T10:20:00Z"), DEVICE_A, "co2", null)));

        List<SensorReadingSnapshot> readings =
                repository.findLatestReadings(latestQuery(Set.of(DEVICE_A)));

        assertTrue(readings.isEmpty());
    }

    @Test
    @DisplayName("운영 중인 기기가 없으면 현재 환경도 묻지 않는다")
    void returnsEmptyLatestReadingsWithoutQuerying() {
        List<SensorReadingSnapshot> readings = repository.findLatestReadings(latestQuery(Set.of()));

        assertTrue(readings.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("기기 식별자 형식이 틀리면 InfluxDB에 묻지 않고 예외를 던진다")
    void rejectsInvalidDeviceEuiBeforeQuerying() {
        try {
            repository.findLatestReadings(latestQuery(Set.of("bad\"eui")));
            fail("예외가 발생해야 하는데 발생하지 않았다");
        } catch (BusinessException exception) {
            verifyNoInteractions(client);
        }
    }
}
