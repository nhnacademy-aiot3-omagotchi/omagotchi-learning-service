package site.omagotchi.learningservice.sensor.infrastructure.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.sensor.application.port.SpaceSeriesRepository;
import site.omagotchi.learningservice.sensor.application.query.SpaceEnvironmentQuery;
import site.omagotchi.learningservice.sensor.application.query.SpaceSeriesQuery;
import site.omagotchi.learningservice.sensor.application.result.SensorReadingSnapshot;
import site.omagotchi.learningservice.sensor.application.result.SensorRef;
import site.omagotchi.learningservice.sensor.domain.SeriesBucket;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.domain.SpaceSeriesPoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
public class InfluxSpaceSeriesRepository implements SpaceSeriesRepository {

    private static final String FLUX_TEMPLATE = """
        import "timezone"
        option location = timezone.location(name: "%s")

        from(bucket: "%s")
          |> range(start: %s, stop: %s)
          |> filter(fn: (r) => r._measurement == "%s")
          |> filter(fn: (r) => r.location == "%s")
          |> filter(fn: (r) => r._field == "value")
          |> aggregateWindow(
                every: %s,
                fn: mean,
                timeSrc: "_start",
                createEmpty: %s)
          |> sort(columns: ["_time"])
        """;

    /**
     * 기기별 마지막 값. 계열마다 마지막 점 하나만 가져오므로 공간이 늘어도 질의는 한 번이다.
     * location 태그로 묶지 않는다 — 그 태그는 게이트웨이가 보낸 이름이라 공간과 어긋날 수 있다.
     */
    private static final String LATEST_FLUX_TEMPLATE = """
        from(bucket: "%s")
          |> range(start: %s, stop: %s)
          |> filter(fn: (r) => r._field == "value")
          |> filter(fn: (r) => contains(value: r._measurement, set: [%s]))
          |> filter(fn: (r) => contains(value: r.device_eui, set: [%s]))
          |> group(columns: ["device_eui", "_measurement"])
          |> last()
        """;

    private static final Pattern LOCATION = Pattern.compile("^[가-힣A-Za-z0-9 _-]{1,32}$");
    private static final Pattern MEASUREMENT = Pattern.compile("^[A-Za-z0-9_]{1,32}$");
    private static final Pattern DEVICE_EUI = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private final InfluxDBClient client;
    private final SensorInfluxProperties properties;

    public InfluxSpaceSeriesRepository(InfluxDBClient client, SensorInfluxProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** 두 구간을 각각 읽어서 이어붙인다 */
    @Override
    public SpaceSeries findSpaceSeries(SpaceSeriesQuery query) {
        requireValid(query);

        if (query.includedDeviceEuis().isEmpty()) {
            return new SpaceSeries(query.location(), query.measurement(), query.window(),
                    query.from(), query.to(), List.of(), List.of());
        }

        Map<String, String> pointByEui = new LinkedHashMap<>();

        List<SpaceSeriesPoint> points = new ArrayList<>();
        // 확정 구간
        points.addAll(fetch(query, query.window().settledBucket(),
                query.from(), query.boundary(), true, false, pointByEui));
        // 진행 중 구간
        points.addAll(fetch(query, query.window().hotBucket(),
                query.boundary(), query.to(), false, true, pointByEui));

        List<SensorRef> sensors = pointByEui.entrySet().stream()
                .map(entry -> new SensorRef(entry.getKey(), entry.getValue(), null))
                .toList();

        return new SpaceSeries(query.location(), query.measurement(), query.window(),
                query.from(), query.to(), sensors, points);
    }

    /** 시간축 없이 기기별 마지막 값만 읽는다. 공간 묶음과 평균은 서비스가 한다. */
    @Override
    public List<SensorReadingSnapshot> findLatestReadings(SpaceEnvironmentQuery query) {
        if (query.deviceEuis().isEmpty() || query.measurement().isEmpty()) {
            return List.of();
        }
        requireValid(query);

        String flux = LATEST_FLUX_TEMPLATE.formatted(
                properties.buckets().raw(),
                query.from().toString(),
                query.to().toString(),
                toFluxSet(query.measurement()),
                toFluxSet(query.deviceEuis()));

        List<SensorReadingSnapshot> readings = new ArrayList<>();

        for (FluxTable table : client.getQueryApi().query(flux, properties.org())) {
            for (FluxRecord record : table.getRecords()) {
                Instant time = record.getTime();
                String deviceEui = text(record, "device_eui");
                String measurement = record.getMeasurement();

                if (time == null || deviceEui == null || measurement == null) {
                    continue;
                }
                if (!(record.getValue() instanceof Number number)) {
                    continue;
                }

                readings.add(new SensorReadingSnapshot(
                        deviceEui, measurement, number.doubleValue(), time));
            }
        }

        return readings;
    }

    private static String toFluxSet(Collection<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(", "));
    }

    private List<SpaceSeriesPoint> fetch(SpaceSeriesQuery query, SeriesBucket bucket,
                                         Instant start, Instant stop,
                                         boolean createEmpty, boolean partial,
                                         Map<String, String> pointByEui) {

        String flux = FLUX_TEMPLATE.formatted(
                query.zone(),
                bucketName(bucket), start.toString(), stop.toString(),
                query.measurement(), query.location(),
                query.window().fluxInterval(), createEmpty);

        List<FluxTable> tables = client.getQueryApi().query(flux, properties.org());

        // 시각별로 모은다.
        Map<Instant, Bucket> byTime = new TreeMap<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Instant time = record.getTime();
                if (time == null) {
                    continue;
                }

                String deviceEui = text(record, "device_eui");

                // 운영 대상이 아닌 기기는 건너뛴다.
                if (deviceEui == null || !query.includedDeviceEuis().contains(deviceEui)) {
                    continue;
                }

                pointByEui.putIfAbsent(deviceEui, text(record, "point"));

                Bucket slot = byTime.computeIfAbsent(time, key -> new Bucket());
                if (record.getValue() instanceof Number number) {
                    slot.add(deviceEui, number.doubleValue());
                }
            }
        }

        return byTime.entrySet().stream()
                .map(entry -> entry.getValue().toPoint(entry.getKey(), partial))
                .toList();
    }

    /** 한 시각에 들어온 센서 값들을 모아 평균·최소·최대를 낸다. */
    private static final class Bucket {
        private double sum;
        private int count;
        private double min = Double.MAX_VALUE;
        private double max = -Double.MAX_VALUE;
        private String minEui;
        private String maxEui;

        void add(String deviceEui, double value) {
            sum += value;
            count++;
            if (value < min) {
                min = value;
                minEui = deviceEui;
            }
            if (value > max) {
                max = value;
                maxEui = deviceEui;
            }
        }

        SpaceSeriesPoint toPoint(Instant time, boolean partial) {
            if (count == 0) {
                return SpaceSeriesPoint.empty(time, partial);
            }
            return new SpaceSeriesPoint(time, sum / count, min, minEui, max, maxEui, count, partial);
        }
    }

    private static String text(FluxRecord record, String key) {
        Object value = record.getValueByKey(key);
        return value == null ? null : value.toString();
    }

    private String bucketName(SeriesBucket bucket) {
        return switch (bucket) {
            case RAW -> properties.buckets().raw();
            case AVG_1H -> properties.buckets().avg1h();
            case AVG_1D -> properties.buckets().avg1d();
        };
    }

    /** 값을 질의문에 직접 넣으므로, 따옴표 같은 글자가 섞이지 못하게 막는다. */
    private void requireValid(SpaceSeriesQuery query) {
        if (!LOCATION.matcher(query.location()).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        if (!MEASUREMENT.matcher(query.measurement()).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private void requireValid(SpaceEnvironmentQuery query) {
        boolean valid = query.measurement().stream().allMatch(MEASUREMENT.asMatchPredicate())
                && query.deviceEuis().stream().allMatch(DEVICE_EUI.asMatchPredicate());
        if (!valid) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}