package site.omagotchi.learningservice.space.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.occupancy.application.OccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 공간 목록의 파생 계산과 노출 정책.
 *
 * <p>사용 상태는 저장되지 않고 활성 점유의 존재로 계산되며(ADR 0003), 그 판정 근거는
 * {@code occupancy}가 제공한다. 여기서 검증하는 것은 <b>받은 사실을 화면 상태로 옮기는
 * 규칙</b>이다 — 어떤 유형에 상태를 매길지, 타 기수 점유를 어떻게 가릴지(MR-36).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("공간 목록 조회")
class SpaceQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");
    private static final OffsetDateTime EXPIRES_AT =
            OffsetDateTime.ofInstant(NOW.plusSeconds(1_800), SEOUL);

    private static final UUID REQUESTER_ID = UUID.randomUUID();
    private static final UUID OCCUPIER_ID = UUID.randomUUID();
    private static final UUID PARTICIPANT_ID = UUID.randomUUID();
    private static final Long MY_COHORT = 11L;
    private static final Long OTHER_COHORT = 99L;

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private OccupancyQueryService occupancyQueryService;

    @Mock
    private CohortAccessService cohortAccessService;

    private SpaceQueryService spaceQueryService;

    @BeforeEach
    void setUp() {
        spaceQueryService = new SpaceQueryService(
                spaceRepository,
                occupancyQueryService,
                cohortAccessService,
                Clock.fixed(NOW, SEOUL)
        );
    }

    @Test
    @DisplayName("공간이 없으면 점유를 조회하지 않는다")
    void skipsOccupancyLookupWhenNoSpaces() {
        when(spaceRepository.findAllNotDeleted()).thenReturn(List.of());

        assertThat(spaceQueryService.getSpaceList(REQUESTER_ID)).isEmpty();

        verifyNoInteractions(occupancyQueryService, cohortAccessService);
    }

    @Test
    @DisplayName("활성 회의실에 점유가 없으면 사용 가능이다")
    void marksActiveMeetingWithoutOccupancyAsAvailable() {
        when(spaceRepository.findAllNotDeleted())
                .thenReturn(List.of(meeting(1L, SpaceOperationalStatus.ACTIVE)));
        when(occupancyQueryService.findActiveBySpaceIds(anyCollection(), any()))
                .thenReturn(Map.of());

        SpaceListResult result = spaceQueryService.getSpaceList(null).getFirst();

        assertThat(result.status()).isEqualTo(SpaceUsageStatus.AVAILABLE);
        assertThat(result.occupancyExpiresAt()).isNull();
        assertThat(result.remainingTimeSeconds()).isNull();
    }

    @Test
    @DisplayName("활성 점유가 있으면 사용 중이고 남은 시간을 함께 낸다")
    void marksOccupiedMeetingWithRemainingTime() {
        stubMeetingWithOccupancy(MY_COHORT);
        when(cohortAccessService.findActiveCohortIds(REQUESTER_ID))
                .thenReturn(List.of(MY_COHORT));

        SpaceListResult result = spaceQueryService.getSpaceList(REQUESTER_ID).getFirst();

        assertThat(result.status()).isEqualTo(SpaceUsageStatus.OCCUPIED);
        assertThat(result.occupancyExpiresAt()).isEqualTo(
                EXPIRES_AT.atZoneSameInstant(SEOUL));
        assertThat(result.remainingTimeSeconds()).isEqualTo(1_800L);
    }

    /**
     * MR-36 — 같은 기수의 점유만 사람을 보여준다.
     */
    @Test
    @DisplayName("같은 기수 점유면 점유자와 참여자를 노출한다")
    void exposesOccupantsToSameCohortRequester() {
        stubMeetingWithOccupancy(MY_COHORT);
        when(cohortAccessService.findActiveCohortIds(REQUESTER_ID))
                .thenReturn(List.of(MY_COHORT));

        SpaceListResult result = spaceQueryService.getSpaceList(REQUESTER_ID).getFirst();

        assertThat(result.occupiedBySameCohort()).isTrue();
        assertThat(result.occupancyCohortId()).isEqualTo(MY_COHORT);
        assertThat(result.occupierUserId()).isEqualTo(OCCUPIER_ID);
        assertThat(result.occupierMembershipId()).isEqualTo(77L);
        assertThat(result.participantUserIds()).containsExactly(PARTICIPANT_ID);
    }

    @Test
    @DisplayName("타 기수 점유면 개인정보를 가린다")
    void hidesOccupantsFromOtherCohortRequester() {
        stubMeetingWithOccupancy(OTHER_COHORT);
        when(cohortAccessService.findActiveCohortIds(REQUESTER_ID))
                .thenReturn(List.of(MY_COHORT));

        SpaceListResult result = spaceQueryService.getSpaceList(REQUESTER_ID).getFirst();

        assertThat(result.status()).isEqualTo(SpaceUsageStatus.OCCUPIED);
        assertOccupantsHidden(result);
    }

    /**
     * 요청자를 알 수 없으면 어떤 기수와도 일치하지 않아 전부 가려진다 — 판정할 수 없을 때
     * 감추는 쪽이 안전한 기본값이다.
     */
    @Test
    @DisplayName("요청자를 특정할 수 없으면 개인정보를 가린다")
    void hidesOccupantsWhenRequesterIsUnknown() {
        stubMeetingWithOccupancy(MY_COHORT);

        SpaceListResult result = spaceQueryService.getSpaceList(null).getFirst();

        assertOccupantsHidden(result);
        verifyNoInteractions(cohortAccessService);
    }

    /**
     * 점유자의 기수를 알 수 없는 경우 — {@code SpaceOccupancyView.of}가 문서화한 계약이다.
     *
     * <p>멤버십이 이미 정리되면 {@code occupierCohortId}가 {@code null}로 온다. 그때는 어느
     * 기수와도 일치하지 않으므로 감춰야 하는데, <b>불변 Set의 {@code contains(null)}은 NPE라</b>
     * 검사 순서를 뒤집으면 점유 하나 때문에 목록 조회 전체가 500이 된다.</p>
     */
    @Test
    @DisplayName("점유자의 기수를 알 수 없으면 500이 아니라 개인정보를 가린다")
    void hidesOccupantsWhenOccupierCohortIsUnknown() {
        stubMeetingWithOccupancy(null);
        when(cohortAccessService.findActiveCohortIds(REQUESTER_ID))
                .thenReturn(List.of(MY_COHORT));

        SpaceListResult result = spaceQueryService.getSpaceList(REQUESTER_ID).getFirst();

        assertThat(result.status()).isEqualTo(SpaceUsageStatus.OCCUPIED);
        assertOccupantsHidden(result);
    }

    /**
     * 같은 상황을 비로그인 경로에서도 확인한다.
     *
     * <p>이쪽은 {@code Set.of()}를 쓰는데 <b>빈 불변 Set도 {@code contains(null)}에서 NPE다.</b>
     * "비어 있으니 안전하겠지"가 성립하지 않아 따로 고정한다.</p>
     */
    @Test
    @DisplayName("비로그인 요청에서 점유자 기수가 없어도 목록이 깨지지 않는다")
    void hidesOccupantsWhenBothRequesterAndOccupierCohortAreUnknown() {
        stubMeetingWithOccupancy(null);

        SpaceListResult result = spaceQueryService.getSpaceList(null).getFirst();

        assertThat(result.status()).isEqualTo(SpaceUsageStatus.OCCUPIED);
        assertOccupantsHidden(result);
        verifyNoInteractions(cohortAccessService);
    }

    /**
     * 비활성 판정이 점유 판정보다 앞선다. 비활성화 직전에 시작된 점유가 남아 있어도
     * 화면에는 "이용 불가"가 맞다.
     */
    @Test
    @DisplayName("비활성 회의실은 점유가 남아 있어도 이용 불가이며 점유 정보를 내지 않는다")
    void marksInactiveMeetingUnavailableEvenWithOccupancy() {
        when(spaceRepository.findAllNotDeleted())
                .thenReturn(List.of(meeting(1L, SpaceOperationalStatus.INACTIVE)));
        when(occupancyQueryService.findActiveBySpaceIds(anyCollection(), any()))
                .thenReturn(Map.of(1L, occupancy(MY_COHORT)));
        when(cohortAccessService.findActiveCohortIds(REQUESTER_ID))
                .thenReturn(List.of(MY_COHORT));

        SpaceListResult result = spaceQueryService.getSpaceList(REQUESTER_ID).getFirst();

        assertThat(result.status()).isEqualTo(SpaceUsageStatus.UNAVAILABLE);
        assertThat(result.occupancyExpiresAt()).isNull();
        assertOccupantsHidden(result);
    }

    @Test
    @DisplayName("회의실이 아닌 공간은 상태가 해당 없음이고 점유를 무시한다")
    void marksNonMeetingSpacesNotApplicable() {
        when(spaceRepository.findAllNotDeleted()).thenReturn(List.of(
                space(1L, SpaceType.LAB, SpaceOperationalStatus.ACTIVE),
                space(2L, SpaceType.STUDY, SpaceOperationalStatus.INACTIVE)
        ));
        when(occupancyQueryService.findActiveBySpaceIds(anyCollection(), any()))
                .thenReturn(Map.of(1L, occupancy(MY_COHORT)));

        List<SpaceListResult> results = spaceQueryService.getSpaceList(null);

        assertThat(results)
                .isNotEmpty()
                .allSatisfy(result -> {
                    assertThat(result.status()).isEqualTo(SpaceUsageStatus.NOT_APPLICABLE);
                    assertThat(result.occupancyExpiresAt()).isNull();
                    assertThat(result.remainingTimeSeconds()).isNull();
        });
    }

    /**
     * 공간이 N개여도 점유 조회는 1회다 — 목록을 돌며 단건으로 물으면 그대로 N+1이 된다.
     */
    @Test
    @DisplayName("점유 조회는 공간 수와 무관하게 한 번만 호출한다")
    void queriesOccupancyOnceRegardlessOfSpaceCount() {
        when(spaceRepository.findAllNotDeleted()).thenReturn(List.of(
                meeting(1L, SpaceOperationalStatus.ACTIVE),
                meeting(2L, SpaceOperationalStatus.ACTIVE),
                meeting(3L, SpaceOperationalStatus.ACTIVE)
        ));
        when(occupancyQueryService.findActiveBySpaceIds(anyCollection(), any()))
                .thenReturn(Map.of());

        spaceQueryService.getSpaceList(null);

        verify(occupancyQueryService, times(1)).findActiveBySpaceIds(anyCollection(), any());
    }

    private void stubMeetingWithOccupancy(Long occupierCohortId) {
        when(spaceRepository.findAllNotDeleted())
                .thenReturn(List.of(meeting(1L, SpaceOperationalStatus.ACTIVE)));
        when(occupancyQueryService.findActiveBySpaceIds(anyCollection(), any()))
                .thenReturn(Map.of(1L, occupancy(occupierCohortId)));
    }

    private static void assertOccupantsHidden(SpaceListResult result) {
        assertThat(result.occupiedBySameCohort()).isFalse();
        assertThat(result.occupancyCohortId()).isNull();
        assertThat(result.occupierUserId()).isNull();
        assertThat(result.occupierMembershipId()).isNull();
        assertThat(result.participantUserIds()).isNull();
    }

    private static SpaceOccupancyView occupancy(Long occupierCohortId) {
        return new SpaceOccupancyView(
                5L, 1L, EXPIRES_AT.minusHours(1), EXPIRES_AT,
                occupierCohortId, 77L, OCCUPIER_ID,
                List.of(PARTICIPANT_ID)
        );
    }

    private static Space meeting(Long id, SpaceOperationalStatus status) {
        return space(id, SpaceType.MEETING, status);
    }

    private static Space space(Long id, SpaceType type, SpaceOperationalStatus status) {
        ZonedDateTime createdAt = ZonedDateTime.ofInstant(NOW, SEOUL);
        return Space.restore(
                id,
                MY_COHORT,
                "공간 " + id,
                type,
                8,
                status,
                status == SpaceOperationalStatus.INACTIVE ? "점검" : null,
                createdAt,
                createdAt,
                null
        );
    }
}
