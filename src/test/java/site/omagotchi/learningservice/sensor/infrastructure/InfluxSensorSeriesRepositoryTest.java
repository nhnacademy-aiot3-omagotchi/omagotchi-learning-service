package site.omagotchi.learningservice.sensor.infrastructure;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.query.SensorSeriesQuery;
import site.omagotchi.learningservice.sensor.domain.SeriesPoint;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.sensor.infrastructure.influx.InfluxSensorSeriesRepository;
import site.omagotchi.learningservice.sensor.infrastructure.influx.SensorInfluxProperties;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

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
@DisplayName("Influx 센서 시계열 저장소")
class InfluxSensorSeriesRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:30:00Z");
    private static final Instant BOUNDARY = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant FROM = Instant.parse("2026-08-24T10:30:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private InfluxDBClient client;

    @Mock
    private QueryApi queryApi;

    private InfluxSensorSeriesRepository repository;

    @BeforeEach
    void setUp() {
        SensorInfluxProperties properties = new SensorInfluxProperties(
                "http://localhost:8086", "test-token", "test-org",
                new SensorInfluxProperties.Buckets("raw-bucket", "avg1h-bucket", "avg1d-bucket"));
        repository = new InfluxSensorSeriesRepository(client, properties);
    }

    /** InfluxDB가 돌려준 것처럼 꾸민 조회 결과를 만든다. */
    private List<FluxTable> tableWithOneRecord(Instant time, Object value) {
        FluxRecord record = new FluxRecord(0);
        record.getValues().put("_time", time);
        record.getValues().put("_value", value);

        FluxTable table = new FluxTable();
        table.getRecords().add(record);
        return List.of(table);
    }

    @Test
    @DisplayName("확정 구간과 진행 중 구간을 각각 조회해 이어붙인다")
    void concatenatesSettledAndHotRanges() {
        // given: 첫 번째 query 호출(확정)과 두 번째 호출(진행 중)의 대답을 순서대로 정한다
        when(client.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), anyString())).thenReturn(
                tableWithOneRecord(Instant.parse("2026-08-25T09:00:00Z"), 23.5),
                tableWithOneRecord(Instant.parse("2026-08-25T10:00:00Z"), 24.0));

        SensorSeriesQuery query = new SensorSeriesQuery(
                "0011223344556677", "temperature", SeriesWindow.DAY,
                FROM, BOUNDARY, NOW, SEOUL);

        // when
        List<SeriesPoint> points = repository.findSeries(query);

        // then: 두 구간의 점이 순서대로 합쳐진다
        assertEquals(2, points.size());
        assertEquals(23.5, points.get(0).value());
        assertFalse(points.get(0).partial());   // 확정 구간의 점
        assertEquals(24.0, points.get(1).value());
        assertTrue(points.get(1).partial());    // 진행 중 구간의 점

        // 그리고 조회는 정확히 두 번 일어나야 한다
        verify(queryApi, times(2)).query(anyString(), anyString());
    }

    @Test
    @DisplayName("값이 숫자가 아니면 null 값의 점으로 바꾼다")
    void convertsNonNumberValueToNull() {
        // given: 확정 구간은 숫자가 아닌 값, 진행 중 구간은 빈 결과
        when(client.getQueryApi()).thenReturn(queryApi);
        when(queryApi.query(anyString(), anyString())).thenReturn(
                tableWithOneRecord(Instant.parse("2026-08-25T09:00:00Z"), "not-a-number"),
                List.of());

        SensorSeriesQuery query = new SensorSeriesQuery(
                "0011223344556677", "temperature", SeriesWindow.DAY,
                FROM, BOUNDARY, NOW, SEOUL);

        // when
        List<SeriesPoint> points = repository.findSeries(query);

        // then
        assertEquals(1, points.size());
        assertNull(points.get(0).value());
    }

    @Test
    @DisplayName("deviceEui 형식이 틀리면 InfluxDB에 묻지 않고 예외를 던진다")
    void rejectsInvalidDeviceEuiBeforeQuerying() {
        SensorSeriesQuery query = new SensorSeriesQuery(
                "bad\"eui", "temperature", SeriesWindow.DAY,
                FROM, BOUNDARY, NOW, SEOUL);

        try {
            repository.findSeries(query);
            fail("예외가 발생해야 하는데 발생하지 않았다");
        } catch (BusinessException exception) {
            // 통과. 그리고 client는 한 번도 안 건드렸어야 한다
            verifyNoInteractions(client);
        }
    }

    @Test
    @DisplayName("measurement 형식이 틀리면 InfluxDB에 묻지 않고 예외를 던진다")
    void rejectsInvalidMeasurementBeforeQuerying() {
        SensorSeriesQuery query = new SensorSeriesQuery(
                "0011223344556677", "bad measurement!", SeriesWindow.DAY,
                FROM, BOUNDARY, NOW, SEOUL);

        try {
            repository.findSeries(query);
            fail("예외가 발생해야 하는데 발생하지 않았다");
        } catch (BusinessException exception) {
            verifyNoInteractions(client);
        }
    }
}