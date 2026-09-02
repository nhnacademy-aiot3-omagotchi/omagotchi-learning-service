package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.sensor.application.SensorSeriesService;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.domain.SpaceSeriesPoint;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.application.result.SpaceNameResult;
import site.omagotchi.learningservice.study.application.result.SpaceEnvironmentSeries;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 공간 하나의 시간대별 환경값을 학습 분석이 쓰기 좋은 형태로 모아 온다.
 *
 * <p>센서 파트의 공개 서비스만 사용한다. 시계열은 {@link SensorSeriesService}, 공간 이름은
 * {@link SpaceQueryService}가 소유하고 있으므로 여기서는 조합만 한다.</p>
 *
 * <p>공간별 기준치(임계값)는 쓰지 않는다 — 그 조회가 관리자 권한을 요구해 학생이 호출할 수
 * 없다. 높고 낮음의 판단은 분석 단계에서 상대 비교로 한다.</p>
 *
 * <p><b>알려진 가정</b>: 센서 시계열 조회는 공간 이름(location 태그)으로 한다. 즉
 * "공간의 이름 == 센서가 붙인 location 태그"를 전제한다. 이름이 다르면 값이 비어 돌아오고,
 * 분석은 그 공간을 "환경 데이터 없음"으로 처리한다 — 틀린 값을 지어내지는 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceEnvironmentQueryService {

    private final SensorSeriesService sensorSeriesService;
    private final SpaceQueryService spaceQueryService;

    /**
     * 공간의 시간대별 평균값을 조회한다.
     *
     * <p>공간 이름을 인자로 받는 이유는 호출 비용 때문이다. 센서 시계열은 이름(location)으로
     * 조회하는데, 한 공간에 대해 측정항목 수만큼 이 메서드가 불린다. 이름을 여기서 찾으면
     * 같은 공간 목록 조회가 항목마다 반복된다 — 호출부가 한 번 찾아 넘기게 한다.</p>
     *
     * @param spaceName 공간 이름. null이면 조회하지 않고 빈 시계열을 돌려준다
     * @param window    조회 창. "day"는 최근 1일, "week"는 최근 7일을 시간 단위로 돌려준다
     */
    public SpaceEnvironmentSeries getHourlySeries(
            Long cohortId,
            UUID requesterUserId,
            Long spaceId,
            String spaceName,
            String measurement,
            String window
    ) {
        Map<Instant, Double> hourlyAverages = new HashMap<>();

        if (spaceName != null) {
            SpaceSeries series = sensorSeriesService.getSpaceSeries(
                    cohortId,
                    requesterUserId,
                    spaceName,
                    measurement,
                    window
            );
            for (SpaceSeriesPoint point : series.points()) {
                // 수집이 없던 시간대는 avg가 null이다. 그런 칸은 담지 않는다
                if (point.avg() != null) {
                    hourlyAverages.put(point.time(), point.avg());
                }
            }
        }

        return new SpaceEnvironmentSeries(spaceId, spaceName, measurement, hourlyAverages);
    }

    /**
     * 공간 ID → 이름 사전. 여러 공간·여러 항목을 조회하기 전에 한 번만 부르면 된다.
     */
    public Map<Long, String> findSpaceNames() {
        Map<Long, String> names = new HashMap<>();
        for (SpaceNameResult name : spaceQueryService.findAllSpaceNames()) {
            names.put(name.spaceId(), name.name());
        }
        return names;
    }
}
