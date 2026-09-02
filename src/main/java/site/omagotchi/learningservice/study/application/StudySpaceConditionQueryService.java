package site.omagotchi.learningservice.study.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.study.application.result.SpaceEnvironmentSeries;
import site.omagotchi.learningservice.study.application.result.StudySpaceConditionResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "지금 어디서 공부하면 좋은가"에 답할 재료를 모은다.
 *
 * <p>내 기수가 쓰는 공간들을 훑어 각 공간의 <b>가장 최근 시간대</b> 이산화탄소·온도·습도를
 * 붙이고, 이산화탄소가 낮은(환기가 잘 된) 순으로 정렬한다. 순서는 정렬 기준일 뿐이고
 * 어디를 권할지는 세 값을 함께 보고 받는 쪽이 정한다.</p>
 *
 * <p>점유자·참여자 같은 개인 식별 정보는 담지 않는다. 사용 상태(비어 있음·사용 중)만
 * 전달한다 — 자리가 있는지는 알아야 하지만 누가 쓰는지는 알 필요가 없다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudySpaceConditionQueryService {

    private static final String CO2 = "co2";
    private static final String TEMPERATURE = "temperature";
    private static final String HUMIDITY = "humidity";
    // 최근 값 하나만 필요하므로 가장 짧은 창을 쓴다. 시간 단위 평균이 돌아온다
    private static final String SERIES_WINDOW = "day";

    private final CohortAccessService cohortAccessService;
    private final SpaceQueryService spaceQueryService;
    private final SpaceEnvironmentQueryService spaceEnvironmentQueryService;

    public StudySpaceConditionResult getCurrentConditions(UUID userId) {
        CohortMembership membership = cohortAccessService.requireCurrentActiveMembership(userId);
        Long cohortId = membership.getCohortId();

        // 내 기수에 배정된, 운영 중인 공간만 후보로 둔다
        List<SpaceListResult> candidates = new ArrayList<>();
        for (SpaceListResult space : spaceQueryService.getSpaceList(userId)) {
            if (!cohortId.equals(space.cohortId())) {
                continue;
            }
            if (space.operationalStatus() != SpaceOperationalStatus.ACTIVE) {
                continue;
            }
            candidates.add(space);
        }
        if (candidates.isEmpty()) {
            return StudySpaceConditionResult.noSpace();
        }

        List<StudySpaceConditionResult.SpaceCondition> conditions = new ArrayList<>();
        boolean anyValue = false;
        for (SpaceListResult space : candidates) {
            // 공간 목록에 이름이 이미 있으므로 그대로 넘긴다. 조회 서비스가 다시 찾지 않게 한다
            SpaceEnvironmentSeries co2Series = spaceEnvironmentQueryService.getHourlySeries(
                    cohortId, userId, space.spaceId(), space.name(), CO2, SERIES_WINDOW);
            SpaceEnvironmentSeries temperatureSeries = spaceEnvironmentQueryService.getHourlySeries(
                    cohortId, userId, space.spaceId(), space.name(), TEMPERATURE, SERIES_WINDOW);
            SpaceEnvironmentSeries humiditySeries = spaceEnvironmentQueryService.getHourlySeries(
                    cohortId, userId, space.spaceId(), space.name(), HUMIDITY, SERIES_WINDOW);

            // 세 항목의 최신 시각이 다를 수 있어, 이산화탄소 기준 시각을 대표로 삼는다
            Instant latestTime = latestTimeOf(co2Series);
            if (latestTime == null) {
                latestTime = latestTimeOf(temperatureSeries);
            }
            if (latestTime == null) {
                latestTime = latestTimeOf(humiditySeries);
            }

            Double co2 = latestValueOf(co2Series);
            Double temperature = latestValueOf(temperatureSeries);
            Double humidity = latestValueOf(humiditySeries);
            if (co2 != null || temperature != null || humidity != null) {
                anyValue = true;
            }

            conditions.add(new StudySpaceConditionResult.SpaceCondition(
                    space.spaceId(),
                    space.name(),
                    space.spaceType() == null ? null : space.spaceType().name(),
                    space.status() == null ? null : space.status().name(),
                    co2,
                    temperature,
                    humidity,
                    latestTime
            ));
        }

        // 값이 낮은 공간이 앞. 값이 없는 공간은 맨 뒤로 보낸다
        conditions.sort(new Comparator<StudySpaceConditionResult.SpaceCondition>() {
            @Override
            public int compare(
                    StudySpaceConditionResult.SpaceCondition a,
                    StudySpaceConditionResult.SpaceCondition b
            ) {
                if (a.co2() == null && b.co2() == null) {
                    return 0;
                }
                if (a.co2() == null) {
                    return 1;
                }
                if (b.co2() == null) {
                    return -1;
                }
                return Double.compare(a.co2(), b.co2());
            }
        });

        if (!anyValue) {
            return StudySpaceConditionResult.noSensorData(conditions);
        }
        return new StudySpaceConditionResult(StudySpaceConditionResult.Status.OK, conditions);
    }

    /** 가장 최근 시간대의 값. 값이 하나도 없으면 null이다. */
    private Double latestValueOf(SpaceEnvironmentSeries series) {
        Instant latest = latestTimeOf(series);
        if (latest == null) {
            return null;
        }
        return series.hourlyAverages().get(latest);
    }

    /** 시계열에서 가장 최근 시간대의 시각. 값이 하나도 없으면 null이다. */
    private Instant latestTimeOf(SpaceEnvironmentSeries series) {
        Instant latest = null;
        for (Map.Entry<Instant, Double> slot : series.hourlyAverages().entrySet()) {
            if (latest == null || slot.getKey().isAfter(latest)) {
                latest = slot.getKey();
            }
        }
        return latest;
    }
}
