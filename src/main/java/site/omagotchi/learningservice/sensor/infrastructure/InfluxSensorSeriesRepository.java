package site.omagotchi.learningservice.sensor.infrastructure;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.sensor.application.port.SensorSeriesRepository;
import site.omagotchi.learningservice.sensor.application.query.SensorSeriesQuery;
import site.omagotchi.learningservice.sensor.domain.SeriesBucket;
import site.omagotchi.learningservice.sensor.domain.SeriesPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class InfluxSensorSeriesRepository implements SensorSeriesRepository {

    private static final String FLUX = """
            from(bucket: params.bucket)
              |> range(start: time(v: params.start), stop: time(v: params.stop))
              |> filter(fn: (r) => r._measurement == params.measurement)
              |> filter(fn: (r) => r.device_eui == params.deviceEui)
              |> filter(fn: (r) => r._field == "value")
              |> aggregateWindow(
                    every: duration(v: params.interval),
                    fn: mean,
                    timeSrc: "_start",
                    createEmpty: params.createEmpty)
              |> sort(columns: ["_time"])
            """;

    private final InfluxDBClient client;
    private final SensorInfluxProperties properties;

    public InfluxSensorSeriesRepository(InfluxDBClient client, SensorInfluxProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** 두 구간을 각각 읽어서 이어붙인다 */
    @Override
    public List<SeriesPoint> findSeries(SensorSeriesQuery query) {
        List<SeriesPoint> points = new ArrayList<>();

        // 확정 구간: 빈 시간대를 null로 남긴다. 안 남기면 차트가 그 구간을 이어 그려 데이터가 있는 것처럼 보인다
        points.addAll(fetch(query, query.window().settledBucket(),
                query.from(), query.boundary(), true, false));

        // 진행 중 구간: 빈 시간대를 만들지 않는다. 아직 안 온 것뿐인데 수집 실패처럼 보인다
        points.addAll(fetch(query, query.window().hotBucket(),
                query.boundary(), query.to(), false, true));

        return points;
    }

    /** InfluxDB 조회 */
    private List<SeriesPoint> fetch(SensorSeriesQuery query, SeriesBucket bucket,
                                    java.time.Instant start, java.time.Instant stop,
                                    boolean createEmpty, boolean partial) {
        Map<String, Object> params = Map.of(
                "bucket", bucketName(bucket),
                "start", start.toString(),
                "stop", stop.toString(),
                "measurement", query.measurement(),
                "deviceEui", query.deviceEui(),
                "interval", query.window().fluxInterval(),
                "createEmpty", createEmpty
        );

        List<FluxTable> tables = client.getQueryApi().query(FLUX, properties.org(), params);

        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> toPoint(record, partial))
                .toList();
    }

    /** 조회 결과를 우리 형태로 바꾸기 */
    private SeriesPoint toPoint(FluxRecord record, boolean partial) {
        Double value = record.getValue() instanceof Number number
                ? number.doubleValue()
                : null;
        return new SeriesPoint(record.getTime(), value, partial);
    }

    private String bucketName(SeriesBucket bucket) {
        return switch (bucket) {
            case RAW -> properties.buckets().raw();
            case AVG_1H -> properties.buckets().avg1h();
            case AVG_1D -> properties.buckets().avg1d();
        };
    }

}