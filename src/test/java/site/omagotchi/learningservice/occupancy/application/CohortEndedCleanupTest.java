package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.sensor.application.CohortEndedSensorCleanup;
import site.omagotchi.learningservice.space.application.CohortEndedSpaceCleanup;
import site.omagotchi.learningservice.team.application.CohortEndedTeamCleanup;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 기수 종료 5단계의 순서와 실패 정책 (CE-01~05, 명세 08).
 *
 * <p><b>순서는 여기서만 결정적으로 검증된다.</b> 통합 테스트는 관찰 가능한 결과(누가 알림을
 * 받았는가)를 보는데, 순서가 뒤집혀도 CE-02의 삭제가 비동기 발송보다 빠르면 결과가 같아
 * 레이스를 이긴 실행에서는 통과해 버린다 — 역전을 실제로 뒤집어 확인했다. 호출 순서
 * 자체를 {@code InOrder}로 고정해야 그 회귀가 항상 잡힌다.</p>
 */
@ExtendWith(MockitoExtension.class)
class CohortEndedCleanupTest {

    private static final Long COHORT_ID = 3L;
    private static final List<Long> MEMBERSHIP_IDS = List.of(10L, 11L);

    @Mock
    private CohortEndedTeamCleanup teamCleanup;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private VacancyAlertService vacancyAlertService;

    @Mock
    private RoomOccupancyRepository occupancyRepository;

    @Mock
    private EndedMembershipOccupancyCleanup occupancyCleanup;

    @Mock
    private CohortEndedSensorCleanup sensorCleanup;

    @Mock
    private CohortEndedSpaceCleanup spaceCleanup;

    private CohortEndedCleanup cohortEndedCleanup;

    @BeforeEach
    void setUp() {
        cohortEndedCleanup = new CohortEndedCleanup(
                teamCleanup,
                cohortMembershipQueryService,
                vacancyAlertService,
                occupancyRepository,
                occupancyCleanup,
                sensorCleanup,
                spaceCleanup,
                Clock.fixed(Instant.parse("2026-07-24T01:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
        // 조회 실패 테스트가 willThrow로 덮어쓰면 이 스텁이 미사용이 된다 — lenient로 둔다.
        org.mockito.Mockito.lenient()
                .when(cohortMembershipQueryService.findMembershipIds(COHORT_ID))
                .thenReturn(MEMBERSHIP_IDS);
    }

    /**
     * <b>CE-05가 강제하는 순서다.</b> 알림 삭제가 점유 종료보다 뒤로 가면, 점유 종료의 공실
     * 발송이 방금 종료된 기수의 신청을 대기 중으로 보고 그 학생들에게 알림을 보낸다.
     */
    @Test
    @DisplayName("팀 정리 → 알림 삭제 → 점유 종료 → 센서 회수 → 공간 해제 순서를 지킨다.")
    void keepsMandatedStepOrder() {
        givenOneActiveOccupancy();

        cohortEndedCleanup.cleanUp(COHORT_ID);

        InOrder order = inOrder(
                teamCleanup, vacancyAlertService, occupancyRepository, sensorCleanup, spaceCleanup);
        order.verify(teamCleanup).disbandAllByCohort(COHORT_ID);
        order.verify(vacancyAlertService).discardByMemberships(MEMBERSHIP_IDS);
        order.verify(occupancyRepository).findActiveSummariesByOccupierMembershipIds(MEMBERSHIP_IDS);
        // 센서 회수가 공간 해제보다 먼저다. 뒤집히면 spaces.cohort_id가 이미 NULL이라
        // 대상 센서를 하나도 찾지 못하고, 회수되지 못한 센서의 룰이 계속 발화한다.
        order.verify(sensorCleanup).deactivateSensors(COHORT_ID);
        order.verify(spaceCleanup).unassignSpaces(COHORT_ID);
    }

    @Test
    @DisplayName("센서 회수가 실패해도 공간 해제는 진행한다.")
    void sensorCleanupFailureDoesNotBlockSpaceUnassign() {
        givenOneActiveOccupancy();
        willThrow(new IllegalStateException("boom"))
                .given(sensorCleanup).deactivateSensors(COHORT_ID);

        cohortEndedCleanup.cleanUp(COHORT_ID);

        verify(spaceCleanup).unassignSpaces(COHORT_ID);
    }

    /**
     * 단계별 격리의 예외 — CE-02 실패 시 CE-03은 격리라며 진행하지 않는다. 진행하면 순서
     * 역전과 같은 결과가 된다. 기수 단위인 CE-04는 무관하게 진행한다.
     */
    @Test
    @DisplayName("알림 삭제가 실패하면 점유 종료를 건너뛰고 공간 해제는 진행한다.")
    void skipsOccupancyReleaseWhenAlertDiscardFails() {
        willThrow(new IllegalStateException("삭제 실패"))
                .given(vacancyAlertService).discardByMemberships(anyCollection());

        assertThatCode(() -> cohortEndedCleanup.cleanUp(COHORT_ID)).doesNotThrowAnyException();

        verify(occupancyRepository, never()).findActiveSummariesByOccupierMembershipIds(anyCollection());
        verify(spaceCleanup).unassignSpaces(COHORT_ID);
    }

    /**
     * CE-01은 독립 단계다 — 팀 정리가 통째로 실패해도 알림·점유·공간 정리를 막으면 안 된다.
     *
     * <p>팀 <b>하나</b>의 실패가 나머지 팀을 막지 않는 것은 {@code CohortEndedTeamCleanup}의
     * 책임이라 그쪽 테스트가 본다. 여기는 그 단계 전체가 던져도 훅이 계속되는지만 본다.</p>
     */
    @Test
    @DisplayName("팀 정리가 실패해도 나머지 단계는 전부 진행한다.")
    void teamCleanupFailureDoesNotBlockOtherSteps() {
        willThrow(new IllegalStateException("팀 정리 실패"))
                .given(teamCleanup).disbandAllByCohort(COHORT_ID);

        assertThatCode(() -> cohortEndedCleanup.cleanUp(COHORT_ID)).doesNotThrowAnyException();

        verify(vacancyAlertService).discardByMemberships(MEMBERSHIP_IDS);
        verify(spaceCleanup).unassignSpaces(COHORT_ID);
    }

    /**
     * 멤버십 조회가 실패하면 CE-02·03은 대상을 특정할 수 없어 함께 건너뛴다. 기수 단위라
     * 멤버십이 필요 없는 CE-04는 진행한다 — 빠뜨리면 다음 기수 배정이 409로 막힌다.
     */
    @Test
    @DisplayName("멤버십 조회가 실패하면 알림·점유는 건너뛰고 공간 해제는 진행한다.")
    void membershipLookupFailureSkipsMembershipStepsButNotLabs() {
        willThrow(new IllegalStateException("조회 실패"))
                .given(cohortMembershipQueryService).findMembershipIds(COHORT_ID);

        assertThatCode(() -> cohortEndedCleanup.cleanUp(COHORT_ID)).doesNotThrowAnyException();

        verify(vacancyAlertService, never()).discardByMemberships(anyCollection());
        verify(occupancyRepository, never()).findActiveSummariesByOccupierMembershipIds(anyCollection());
        verify(spaceCleanup).unassignSpaces(COHORT_ID);
    }

    /** 점유 한 건의 실패가 나머지 점유와 CE-04를 막지 않는다 — 남은 것은 만료 스케줄러가 받친다. */
    @Test
    @DisplayName("점유 한 건의 정리가 실패해도 나머지 점유와 공간 해제는 진행한다.")
    void oneOccupancyFailureDoesNotBlockOthers() {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        given(occupancyRepository.findActiveSummariesByOccupierMembershipIds(MEMBERSHIP_IDS))
                .willReturn(List.of(
                        new RoomOccupancyRepository.ActiveOccupancy(100L, 10L, firstUser),
                        new RoomOccupancyRepository.ActiveOccupancy(101L, 11L, secondUser)));
        willThrow(new IllegalStateException("정리 실패"))
                .given(occupancyCleanup).cleanUp(org.mockito.ArgumentMatchers.eq(10L), any(), any());

        assertThatCode(() -> cohortEndedCleanup.cleanUp(COHORT_ID)).doesNotThrowAnyException();

        verify(occupancyCleanup).cleanUp(
                org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.eq(secondUser),
                any(OffsetDateTime.class));
        verify(spaceCleanup).unassignSpaces(COHORT_ID);
    }

    private void givenOneActiveOccupancy() {
        given(occupancyRepository.findActiveSummariesByOccupierMembershipIds(MEMBERSHIP_IDS))
                .willReturn(List.of(
                        new RoomOccupancyRepository.ActiveOccupancy(100L, 10L, UUID.randomUUID())));
    }
}
