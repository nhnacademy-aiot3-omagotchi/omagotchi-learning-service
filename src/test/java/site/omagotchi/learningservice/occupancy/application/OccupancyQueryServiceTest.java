package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.ActiveSpaceOccupancy;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 다른 Feature에 노출하는 점유 상태 조회.
 *
 * <p>{@code space} 파트가 공간 목록의 사용 상태를 파생 계산하는 데 쓴다. 이 계약이
 * 자리 잡으면 저쪽이 {@code room_occupancies}를 직접 읽던 엔티티·리포지토리를 지울 수 있다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OccupancyQueryServiceTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final Long MEMBERSHIP_ID = 77L;
    private static final Long COHORT_ID = 3L;
    private static final UUID OCCUPIER_ID = UUID.randomUUID();
    private static final UUID PARTICIPANT_ID = UUID.randomUUID();

    @Mock
    private RoomOccupancyRepository occupancyRepository;

    @Mock
    private OccupancyParticipantRepository participantRepository;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @InjectMocks
    private OccupancyQueryService occupancyQueryService;

    /** 소비처가 공간 목록과 조인하므로 spaceId로 키잡아 돌려준다. */
    @Test
    @DisplayName("사용 중인 회의실을 공간 식별자로 키잡아 돌려준다.")
    void returnsActiveOccupanciesKeyedBySpaceId() {
        givenOccupancies(occupancy(2L));
        given(cohortMembershipQueryService.findCohortIds(List.of(MEMBERSHIP_ID)))
                .willReturn(Map.of(MEMBERSHIP_ID, COHORT_ID));
        given(participantRepository.findActiveUserIdsByOccupancyIds(List.of(10L)))
                .willReturn(Map.of(10L, List.of(OCCUPIER_ID, PARTICIPANT_ID)));

        Map<Long, SpaceOccupancyView> found =
                occupancyQueryService.findActiveBySpaceIds(List.of(1L, 2L), NOW);

        assertThat(found).containsOnlyKeys(2L);
        SpaceOccupancyView view = found.get(2L);
        assertThat(view.expiresAt()).isEqualTo(NOW.plusHours(1));
        assertThat(view.occupierCohortId()).isEqualTo(COHORT_ID);
        assertThat(view.occupierUserId()).isEqualTo(OCCUPIER_ID);
        assertThat(view.participantUserIds()).containsExactly(OCCUPIER_ID, PARTICIPANT_ID);
    }

    /** 비어 있는 방은 키가 없다 — 소비처는 {@code null} 여부로 사용 상태를 판단한다. */
    @Test
    @DisplayName("점유가 없는 회의실은 결과에 담기지 않는다.")
    void excludesRoomsWithoutActiveOccupancy() {
        givenOccupancies();

        assertThat(occupancyQueryService.findActiveBySpaceIds(List.of(1L), NOW)).isEmpty();
    }

    /**
     * 만료 판정을 리포지토리에 위임하되 기준 시각은 호출부가 정한다. 유니크 인덱스는
     * {@code status}만 보고 {@code expires_at}은 보지 않아 만료된 행이 ACTIVE로 남아 있고,
     * 이 필터가 없으면 목록에는 "사용 중"인데 점유는 성공하는 상태가 보인다.
     */
    @Test
    @DisplayName("만료 판정 기준 시각을 그대로 전달한다.")
    void passesThroughExpiryReferenceTime() {
        givenOccupancies();

        occupancyQueryService.findActiveBySpaceIds(List.of(1L), NOW);

        verify(occupancyRepository).findActiveBySpaceIds(List.of(1L), NOW);
    }

    /** 빈 입력에 헛질의를 보내지 않는다. */
    @Test
    @DisplayName("조회할 공간이 없으면 질의하지 않는다.")
    void skipsQueryWhenNoSpaceIds() {
        assertThat(occupancyQueryService.findActiveBySpaceIds(List.of(), NOW)).isEmpty();

        verify(occupancyRepository, never()).findActiveBySpaceIds(any(), any());
    }

    /**
     * 계약 위반을 빈 결과로 덮지 않는다 (04-error-handling §2).
     *
     * <p>이 자리에서 {@code Map.of()}를 돌려주면 소비처는 <b>모든 회의실이 공실</b>이라는
     * 그럴듯한 응답을 받는다. 사용 중인 방이 점유 가능해 보이고 공간 비활성화 가드도
     * 통과하는데, 조회가 실패했다는 사실은 어디에도 남지 않는다.</p>
     */
    @Test
    @DisplayName("인자가 누락되면 빈 결과가 아니라 계약 위반으로 끊는다.")
    void throwsContractViolationOnMissingArgument() {
        assertThatThrownBy(() -> occupancyQueryService.findActiveBySpaceIds(List.of(1L), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> occupancyQueryService.findActiveBySpaceIds(null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> occupancyQueryService.existsActive(1L, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(occupancyRepository, never()).findActiveBySpaceIds(any(), any());
        verify(occupancyRepository, never()).existsActiveBySpaceId(any(), any());
    }

    /**
     * 멤버십이 이미 지워졌으면 기수를 알 수 없다. 그때 임의의 기수를 채우면 남의 기수
     * 사용자에게 점유자 정보가 노출되므로, 판정 불가를 {@code null}로 그대로 알린다 —
     * 소비처의 기수 대조가 실패해 개인정보가 감춰지는 쪽이 안전한 기본값이다.
     */
    @Test
    @DisplayName("점유자 멤버십을 찾지 못하면 기수를 비운다.")
    void leavesCohortEmptyWhenMembershipNotFound() {
        givenOccupancies(occupancy(2L));
        given(cohortMembershipQueryService.findCohortIds(List.of(MEMBERSHIP_ID)))
                .willReturn(Map.of());
        given(participantRepository.findActiveUserIdsByOccupancyIds(List.of(10L)))
                .willReturn(Map.of());

        Map<Long, SpaceOccupancyView> found =
                occupancyQueryService.findActiveBySpaceIds(List.of(2L), NOW);

        assertThat(found.get(2L).occupierCohortId()).isNull();
        assertThat(found.get(2L).participantUserIds()).isEmpty();
    }

    /**
     * 사용 중인 방이 N개여도 참여자·기수 조회는 각 1회다.
     * 점유마다 부르면 공간 목록이 그대로 N+1이 된다.
     */
    @Test
    @DisplayName("점유가 여러 건이어도 참여자·기수 조회는 각각 1회다.")
    void queriesParticipantsAndCohortOnceForMultipleOccupancies() {
        givenOccupancies(occupancy(1L), occupancy(2L));
        given(cohortMembershipQueryService.findCohortIds(any())).willReturn(Map.of());
        given(participantRepository.findActiveUserIdsByOccupancyIds(any())).willReturn(Map.of());

        occupancyQueryService.findActiveBySpaceIds(List.of(1L, 2L), NOW);

        verify(cohortMembershipQueryService).findCohortIds(any());
        verify(participantRepository).findActiveUserIdsByOccupancyIds(any());
    }

    /**
     * 목록과 같은 기준으로 판정해야 한다 — 목록에 "사용 중"으로 뜬 방은
     * 공간 비활성화 가드에서도 사용 중이어야 한다.
     */
    @Test
    @DisplayName("사용 중 여부는 기준 시각과 함께 위임한다.")
    void delegatesActiveCheckWithReferenceTime() {
        given(occupancyRepository.existsActiveBySpaceId(1L, NOW)).willReturn(true);

        assertThat(occupancyQueryService.existsActive(1L, NOW)).isTrue();
    }

    private void givenOccupancies(ActiveSpaceOccupancy... occupancies) {
        given(occupancyRepository.findActiveBySpaceIds(any(), any()))
                .willReturn(List.of(occupancies));
    }

    /** occupancyId를 spaceId에서 파생시켜 두 식별자가 뒤바뀌면 테스트가 깨지게 한다. */
    private ActiveSpaceOccupancy occupancy(Long spaceId) {
        return new ActiveSpaceOccupancy(
                spaceId * 5, spaceId, NOW.minusHours(1), NOW.plusHours(1),
                MEMBERSHIP_ID, OCCUPIER_ID);
    }
}
