package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.space.application.SpaceQueryService;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;
import site.omagotchi.learningservice.study.application.result.SpaceEnvironmentSeries;
import site.omagotchi.learningservice.study.application.result.StudySpaceConditionResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("학습 공간 환경 조건 조회")
@ExtendWith(MockitoExtension.class)
class StudySpaceConditionQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long COHORT_ID = 1L;
    private static final Long LAB_ID = 10L;
    private static final Long OFFICE_ID = 20L;
    private static final Instant MEASURED_AT = Instant.parse("2026-09-04T06:00:00Z");

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private SpaceQueryService spaceQueryService;

    @Mock
    private SpaceEnvironmentQueryService spaceEnvironmentQueryService;

    private StudySpaceConditionQueryService service;

    @BeforeEach
    void setUp() {
        service = new StudySpaceConditionQueryService(
                cohortAccessService,
                spaceQueryService,
                spaceEnvironmentQueryService
        );

        CohortMembership membership = mock(CohortMembership.class);
        given(membership.getCohortId()).willReturn(COHORT_ID);
        given(cohortAccessService.requireCurrentActiveMembership(USER_ID)).willReturn(membership);
    }

    @Test
    @DisplayName("사무실만 있으면 추천 후보가 없다")
    void returnsNoSpaceWhenOnlyOfficeExists() {
        given(spaceQueryService.getSpaceList(USER_ID))
                .willReturn(List.of(activeSpace(OFFICE_ID, "사무실", SpaceType.OFFICE)));

        StudySpaceConditionResult result = service.getCurrentConditions(USER_ID);

        assertThat(result.status()).isEqualTo(StudySpaceConditionResult.Status.NO_SPACE);
        assertThat(result.spaces()).isEmpty();
        verifyNoInteractions(spaceEnvironmentQueryService);
    }

    @Test
    @DisplayName("사무실과 실습실이 함께 있으면 실습실만 조회한다")
    void excludesOfficeAndQueriesOnlyLab() {
        SpaceListResult office = activeSpace(OFFICE_ID, "사무실", SpaceType.OFFICE);
        SpaceListResult lab = activeSpace(LAB_ID, "실습실", SpaceType.LAB);
        given(spaceQueryService.getSpaceList(USER_ID)).willReturn(List.of(office, lab));
        givenEnvironmentSeries(lab);

        StudySpaceConditionResult result = service.getCurrentConditions(USER_ID);

        assertThat(result.status()).isEqualTo(StudySpaceConditionResult.Status.OK);
        assertThat(result.spaces())
                .singleElement()
                .satisfies(space -> {
                    assertThat(space.spaceId()).isEqualTo(LAB_ID);
                    assertThat(space.spaceName()).isEqualTo("실습실");
                    assertThat(space.spaceType()).isEqualTo(SpaceType.LAB.name());
                });
        verify(spaceEnvironmentQueryService, never()).getHourlySeries(
                eq(COHORT_ID), eq(USER_ID), eq(OFFICE_ID), anyString(), anyString(), anyString());
    }

    private SpaceListResult activeSpace(Long spaceId, String name, SpaceType spaceType) {
        return new SpaceListResult(
                spaceId,
                name,
                spaceType,
                30,
                SpaceOperationalStatus.ACTIVE,
                null,
                COHORT_ID,
                SpaceUsageStatus.NOT_APPLICABLE,
                null,
                null
        );
    }

    private void givenEnvironmentSeries(SpaceListResult space) {
        given(spaceEnvironmentQueryService.getHourlySeries(
                COHORT_ID, USER_ID, space.spaceId(), space.name(), "co2", "day"))
                .willReturn(series(space, "co2", 650.0));
        given(spaceEnvironmentQueryService.getHourlySeries(
                COHORT_ID, USER_ID, space.spaceId(), space.name(), "temperature", "day"))
                .willReturn(series(space, "temperature", 24.0));
        given(spaceEnvironmentQueryService.getHourlySeries(
                COHORT_ID, USER_ID, space.spaceId(), space.name(), "humidity", "day"))
                .willReturn(series(space, "humidity", 50.0));
    }

    private SpaceEnvironmentSeries series(SpaceListResult space, String measurement, double value) {
        return new SpaceEnvironmentSeries(
                space.spaceId(),
                space.name(),
                measurement,
                Map.of(MEASURED_AT, value)
        );
    }
}
