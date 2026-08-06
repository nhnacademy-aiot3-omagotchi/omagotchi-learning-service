package site.omagotchi.learningservice.occupancy.application;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.PresenceReader;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.port.SpaceReader;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 참여자 추가·이탈·제외 (MR-19, MR-28~31, MR-33).
 *
 * <p>MR-33이 이 테스트의 존재 이유다. 스키마 v1.3에서 {@code cohort_id} 컬럼과 복합 FK를
 * 제거했으므로 "참여자의 기수 = 점유자의 기수"를 DB가 보장하지 않는다 — 애플리케이션
 * 검증이 유일한 방어선이라 반드시 테스트로 고정한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OccupancyParticipantServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");

    private static final Long SPACE_ID = 1L;
    private static final Long OCCUPANCY_ID = 100L;
    private static final int CAPACITY = 8;

    private static final Long COHORT_ID = 3L;
    private static final Long OTHER_COHORT_ID = 4L;

    private static final Long OCCUPIER_MEMBERSHIP_ID = 10L;
    private static final Long TARGET_MEMBERSHIP_ID = 20L;
    private static final UUID OCCUPIER_USER_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID STRANGER_USER_ID = UUID.randomUUID();

    @Mock
    private SpaceReader spaceReader;

    @Mock
    private PresenceReader presenceReader;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private RoomOccupancyRepository occupancyRepository;

    @Mock
    private OccupancyParticipantRepository participantRepository;

    private Clock clock;
    private OccupancyParticipantService occupancyParticipantService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, SEOUL);
        occupancyParticipantService = new OccupancyParticipantService(
                spaceReader,
                presenceReader,
                cohortMembershipQueryService,
                occupancyRepository,
                participantRepository,
                clock
        );
    }

    // ────────────────────────────── 추가 ──────────────────────────────

    @Test
    @DisplayName("참여자를 추가하면 재실 구간의 멤버십으로 행이 생성된다.")
    void test1() {
        givenAddableTarget();
        given(participantRepository.find(OCCUPANCY_ID, TARGET_USER_ID)).willReturn(Optional.empty());

        occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID);

        ArgumentCaptor<OccupancyParticipant> captor =
                ArgumentCaptor.forClass(OccupancyParticipant.class);
        verify(participantRepository).save(captor.capture());

        OccupancyParticipant saved = captor.getValue();
        assertThat(saved.getOccupancyId()).isEqualTo(OCCUPANCY_ID);
        assertThat(saved.getCohortMembershipId()).isEqualTo(TARGET_MEMBERSHIP_ID);
        assertThat(saved.getUserId()).isEqualTo(TARGET_USER_ID);
        assertThat(saved.getJoinedAt()).isEqualTo(now());
        assertThat(saved.isActive()).isTrue();
    }

    /**
     * 재합류가 새 행이 아닌 이유는 둘이다 — {@code uq_occupancy_participants_pair}가
     * 점유당 사람 1행이라 INSERT가 애초에 들어가지 않고, 행을 지웠다 넣으면 이력이 사라진다.
     */
    @Test
    @DisplayName("이탈했던 사람을 다시 추가하면 새 행 없이 기존 행이 복원된다.")
    void test2() {
        givenAddableTarget();
        OccupancyParticipant left = participant();
        left.leave(now().minusMinutes(10));
        given(participantRepository.find(OCCUPANCY_ID, TARGET_USER_ID)).willReturn(Optional.of(left));

        occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID);

        assertThat(left.isActive()).isTrue();
        assertThat(left.getLeftAt()).isNull();
        assertThat(left.getJoinedAt()).isEqualTo(now().minusHours(1));
        verify(participantRepository, never()).save(any(OccupancyParticipant.class));
    }

    /** DB가 막지 않으므로 이 검증이 유일한 방어선이다 (MR-33). */
    @Test
    @DisplayName("점유자와 다른 기수의 사용자는 참여자로 추가할 수 없다.")
    void test3() {
        givenActiveOccupancy();
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room()));
        givenCohort(OCCUPIER_MEMBERSHIP_ID, COHORT_ID);
        givenTargetPresent();
        givenCohort(TARGET_MEMBERSHIP_ID, OTHER_COHORT_ID);

        assertBusinessError(
                OccupancyErrorCode.DIFFERENT_COHORT,
                () -> occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID)
        );

        verify(participantRepository, never()).save(any(OccupancyParticipant.class));
    }

    /** 종료된 멤버십은 기수를 특정할 수 없어 정합 검증이 성립하지 않는다. */
    @Test
    @DisplayName("대상의 멤버십이 활성이 아니면 추가할 수 없다.")
    void test4() {
        givenActiveOccupancy();
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room()));
        givenCohort(OCCUPIER_MEMBERSHIP_ID, COHORT_ID);
        givenTargetPresent();
        given(cohortMembershipQueryService.findActiveMembership(TARGET_MEMBERSHIP_ID))
                .willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.DIFFERENT_COHORT,
                () -> occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID)
        );
    }

    @Test
    @DisplayName("점유자가 아니면 참여자를 추가할 수 없다.")
    void test5() {
        givenActiveOccupancy();

        assertBusinessError(
                OccupancyErrorCode.NOT_OCCUPIER,
                () -> occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, STRANGER_USER_ID)
        );
    }

    @Test
    @DisplayName("재실이 아닌 사용자는 참여자로 추가할 수 없다.")
    void test6() {
        givenActiveOccupancy();
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room()));
        givenCohort(OCCUPIER_MEMBERSHIP_ID, COHORT_ID);
        given(presenceReader.findOpenPresence(TARGET_USER_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.TARGET_NOT_PRESENT,
                () -> occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID)
        );
    }

    @Test
    @DisplayName("활성 점유가 없으면 이미 종료된 점유로 처리한다.")
    void test7() {
        given(occupancyRepository.findActiveSummaryBySpaceId(SPACE_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID)
        );
    }

    /**
     * 스케줄러가 EXPIRED로 바꾼 직후 도착한 요청을 잡는다. 락 밖 조회는 활성으로 보였지만
     * 락을 잡고 보면 이미 끝나 있는 경우다 — 활성 조건을 락 쿼리에 넣으면 놓친다.
     */
    @Test
    @DisplayName("락을 잡은 뒤 종료된 점유를 찾아낸다.")
    void test8() {
        givenActiveOccupancy();
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room()));
        givenCohort(OCCUPIER_MEMBERSHIP_ID, COHORT_ID);
        givenTargetPresent();
        givenCohort(TARGET_MEMBERSHIP_ID, COHORT_ID);

        RoomOccupancy expired = occupancy();
        ReflectionTestUtils.setField(expired, "status", OccupancyStatus.EXPIRED);
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(expired));

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID)
        );

        verify(participantRepository, never()).save(any(OccupancyParticipant.class));
    }

    /** 정원은 "최대 N행"이라 유니크로 표현할 수 없어 락 안 카운트가 유일한 방어선이다. */
    @Test
    @DisplayName("정원이 찬 회의실에는 참여자를 추가할 수 없다.")
    void test9() {
        givenAddableTarget();
        given(participantRepository.countActiveByOccupancyId(OCCUPANCY_ID)).willReturn((long) CAPACITY);

        assertBusinessError(
                OccupancyErrorCode.CAPACITY_EXCEEDED,
                () -> occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID)
        );

        verify(participantRepository, never()).save(any(OccupancyParticipant.class));
    }

    @Test
    @DisplayName("잔여 1석이면 추가에 성공한다.")
    void test10() {
        givenAddableTarget();
        given(participantRepository.countActiveByOccupancyId(OCCUPANCY_ID))
                .willReturn((long) CAPACITY - 1);
        given(participantRepository.find(OCCUPANCY_ID, TARGET_USER_ID)).willReturn(Optional.empty());

        occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID);

        verify(participantRepository).save(any(OccupancyParticipant.class));
    }

    /**
     * 락 밖에서 세면 잔여 1석에 둘이 동시에 들어와 정원을 넘는다. 순서가 뒤집히면
     * 이 테스트가 먼저 깨져야 한다.
     */
    @Test
    @DisplayName("정원 카운트는 점유 행 락을 잡은 뒤에 한다.")
    void test11() {
        givenAddableTarget();
        given(participantRepository.find(OCCUPANCY_ID, TARGET_USER_ID)).willReturn(Optional.empty());

        occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID);

        InOrder order = inOrder(presenceReader, occupancyRepository, participantRepository);
        order.verify(presenceReader).findOpenPresence(TARGET_USER_ID);
        order.verify(occupancyRepository).lockById(OCCUPANCY_ID);
        order.verify(participantRepository).countActiveByOccupancyId(OCCUPANCY_ID);
        order.verify(participantRepository).save(any(OccupancyParticipant.class));
    }

    /** 점유 중에 공간이 하드 삭제되는 경로는 없지만, 정원을 못 읽으면 통과시켜선 안 된다. */
    @Test
    @DisplayName("공간을 찾을 수 없으면 참여자를 추가하지 않는다.")
    void test20() {
        givenActiveOccupancy();
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.SPACE_NOT_FOUND,
                () -> occupancyParticipantService.add(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID)
        );
    }

    @Test
    @DisplayName("락 시점에 점유 행이 사라지면 이미 종료된 점유로 처리한다.")
    void test21() {
        givenLockedOccupancyMissing();

        assertBusinessError(
                OccupancyErrorCode.OCCUPANCY_ENDED,
                () -> occupancyParticipantService.remove(SPACE_ID, TARGET_USER_ID, TARGET_USER_ID)
        );

        verify(participantRepository, never()).find(any(), any());
    }

    // ────────────────────────────── 이탈·제외 ──────────────────────────────

    @Test
    @DisplayName("참여자 본인이 이탈하면 이탈 시각이 기록된다.")
    void test12() {
        givenLockedOccupancy();
        OccupancyParticipant participant = participant();
        given(participantRepository.find(OCCUPANCY_ID, TARGET_USER_ID))
                .willReturn(Optional.of(participant));

        occupancyParticipantService.remove(SPACE_ID, TARGET_USER_ID, TARGET_USER_ID);

        assertThat(participant.getLeftAt()).isEqualTo(now());
        assertThat(participant.isActive()).isFalse();
    }

    @Test
    @DisplayName("점유자는 참여자를 제외할 수 있다.")
    void test13() {
        givenLockedOccupancy();
        OccupancyParticipant participant = participant();
        given(participantRepository.find(OCCUPANCY_ID, TARGET_USER_ID))
                .willReturn(Optional.of(participant));

        occupancyParticipantService.remove(SPACE_ID, TARGET_USER_ID, OCCUPIER_USER_ID);

        assertThat(participant.isActive()).isFalse();
    }

    @Test
    @DisplayName("점유자도 대상 본인도 아니면 제외할 수 없다.")
    void test14() {
        givenActiveOccupancy();

        assertBusinessError(
                OccupancyErrorCode.NOT_OCCUPIER,
                () -> occupancyParticipantService.remove(SPACE_ID, TARGET_USER_ID, STRANGER_USER_ID)
        );
    }

    /** 점유자가 빠지면 주인 없는 활성 점유가 남는다 — 반납으로만 종료해야 한다. */
    @Test
    @DisplayName("점유자는 이탈로 회의를 떠날 수 없다.")
    void test15() {
        givenActiveOccupancy();

        assertBusinessError(
                OccupancyErrorCode.OCCUPIER_CANNOT_LEAVE,
                () -> occupancyParticipantService.remove(SPACE_ID, OCCUPIER_USER_ID, OCCUPIER_USER_ID)
        );
    }

    /**
     * 권한 판정이 대상 판정보다 먼저다. 여기서 400("점유자는 제외할 수 없다")을 주면
     * 권한 없는 사람에게 "그 사람이 점유자"라는 사실이 새어 나간다.
     */
    @Test
    @DisplayName("권한 없는 사람이 점유자를 제외하려 하면 점유자 여부를 알리지 않고 막는다.")
    void test16() {
        givenActiveOccupancy();

        assertBusinessError(
                OccupancyErrorCode.NOT_OCCUPIER,
                () -> occupancyParticipantService.remove(SPACE_ID, OCCUPIER_USER_ID, TARGET_USER_ID)
        );
    }

    @Test
    @DisplayName("참여자가 아닌 사람은 이탈할 수 없다.")
    void test17() {
        givenLockedOccupancy();
        given(participantRepository.find(OCCUPANCY_ID, TARGET_USER_ID)).willReturn(Optional.empty());

        assertBusinessError(
                OccupancyErrorCode.PARTICIPANT_NOT_FOUND,
                () -> occupancyParticipantService.remove(SPACE_ID, TARGET_USER_ID, TARGET_USER_ID)
        );
    }

    /**
     * 최초 이탈 시각이 참여 구간의 끝이다. 덮어쓰면 실제보다 오래 있었던 것으로 기록된다.
     * 결과 상태가 같으므로 재요청은 409가 아니라 성공으로 둔다.
     */
    @Test
    @DisplayName("이미 이탈한 참여자에게 다시 요청해도 이탈 시각이 바뀌지 않는다.")
    void test18() {
        givenLockedOccupancy();
        OccupancyParticipant participant = participant();
        OffsetDateTime firstLeftAt = now().minusMinutes(30);
        participant.leave(firstLeftAt);
        given(participantRepository.find(OCCUPANCY_ID, TARGET_USER_ID))
                .willReturn(Optional.of(participant));

        occupancyParticipantService.remove(SPACE_ID, TARGET_USER_ID, TARGET_USER_ID);

        assertThat(participant.getLeftAt()).isEqualTo(firstLeftAt);
    }

    @Test
    @DisplayName("이탈도 점유 행 락을 잡은 뒤에 처리한다.")
    void test19() {
        givenLockedOccupancy();
        given(participantRepository.find(OCCUPANCY_ID, TARGET_USER_ID))
                .willReturn(Optional.of(participant()));

        occupancyParticipantService.remove(SPACE_ID, TARGET_USER_ID, TARGET_USER_ID);

        InOrder order = inOrder(occupancyRepository, participantRepository);
        order.verify(occupancyRepository).findActiveSummaryBySpaceId(SPACE_ID);
        order.verify(occupancyRepository).lockById(OCCUPANCY_ID);
        order.verify(participantRepository).find(OCCUPANCY_ID, TARGET_USER_ID);
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void givenActiveOccupancy() {
        given(occupancyRepository.findActiveSummaryBySpaceId(SPACE_ID)).willReturn(
                Optional.of(new RoomOccupancyRepository.ActiveOccupancy(
                        OCCUPANCY_ID, OCCUPIER_MEMBERSHIP_ID, OCCUPIER_USER_ID)));
    }

    private void givenLockedOccupancy() {
        givenActiveOccupancy();
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy()));
    }

    private void givenLockedOccupancyMissing() {
        givenActiveOccupancy();
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.empty());
    }

    /** 추가가 정원 검사까지 도달하는 최소 구성. 정원 카운트는 기본값(0)에 맡긴다. */
    private void givenAddableTarget() {
        givenActiveOccupancy();
        given(spaceReader.find(SPACE_ID)).willReturn(Optional.of(room()));
        givenCohort(OCCUPIER_MEMBERSHIP_ID, COHORT_ID);
        givenTargetPresent();
        givenCohort(TARGET_MEMBERSHIP_ID, COHORT_ID);
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy()));
    }

    private void givenTargetPresent() {
        given(presenceReader.findOpenPresence(TARGET_USER_ID)).willReturn(
                Optional.of(new PresenceReader.PresenceContext(TARGET_MEMBERSHIP_ID)));
    }

    private void givenCohort(Long membershipId, Long cohortId) {
        given(cohortMembershipQueryService.findActiveMembership(membershipId)).willReturn(
                Optional.of(new CohortMembershipView(membershipId, cohortId, TARGET_USER_ID)));
    }

    private SpaceReader.MeetingRoom room() {
        return new SpaceReader.MeetingRoom(SPACE_ID, true, true, CAPACITY);
    }

    private RoomOccupancy occupancy() {
        RoomOccupancy occupancy = RoomOccupancy.start(
                SPACE_ID, OCCUPIER_MEMBERSHIP_ID, OCCUPIER_USER_ID, now(), now().plusHours(2));
        ReflectionTestUtils.setField(occupancy, "id", OCCUPANCY_ID);
        return occupancy;
    }

    private OccupancyParticipant participant() {
        return OccupancyParticipant.join(
                OCCUPANCY_ID, TARGET_MEMBERSHIP_ID, TARGET_USER_ID, now().minusHours(1));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private void assertBusinessError(ErrorCode expectedErrorCode, ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(expectedErrorCode));
    }
}
