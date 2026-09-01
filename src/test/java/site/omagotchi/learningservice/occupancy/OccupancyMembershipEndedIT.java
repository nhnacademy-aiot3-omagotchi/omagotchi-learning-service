package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.occupancy.application.EndedMembershipOccupancyCleanup;
import site.omagotchi.learningservice.occupancy.application.EndedMembershipOccupancySweep;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;

import org.mockito.ArgumentCaptor;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender.VacancyNotice;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 소속 종료에 따른 점유·참여 정리 (MR-26, CE-07 점유 부분).
 *
 * <p><b>정리하지 않으면 방과 사람이 함께 묶인다.</b> 점유는 ACTIVE로 남아 아무도 쓸 수
 * 없고, 열린 참여 행은 {@code uq_occupancy_participants_one_active}가 계정 기준이라 그
 * 사람이 다시는 어떤 회의에도 들어가지 못하게 만든다. 둘 다 DB 제약이 만드는 결과라
 * 실제 PostgreSQL이 있어야 검증된다.</p>
 *
 * <p>정리 규칙은 Application을 직접 불러 동기로 고정하고, 이벤트가 리스너에 닿는지는
 * 마지막 테스트 하나가 따로 확인한다 ({@code TeamMembershipEndedIT}와 같은 구성).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class OccupancyMembershipEndedIT {

    @Autowired
    OccupancyTestFixture fixture;

    @Autowired
    RoomOccupancyService roomOccupancyService;

    @Autowired
    OccupancyParticipantService participantService;

    @Autowired
    VacancyAlertService vacancyAlertService;

    @Autowired
    EndedMembershipOccupancyCleanup occupancyCleanup;

    @Autowired
    EndedMembershipOccupancySweep occupancySweep;

    @Autowired
    CohortMembershipService membershipService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    @MockitoBean
    VacancyAlertSender vacancyAlertSender;

    @BeforeEach
    void stubSuccessfulSendByDefault() {
        // sendVacancyAlert는 boolean을 반환하고 Mock 기본값은 false(건너뜀)이므로,
        // 명시적으로 true(발송 성공)를 스텁한다 — 아니면 정리로 비워진 방의 공실 알림이
        // 소진되지 않아 waitingAlertRows가 0이 되길 기다리는 awaitUntil이 타임아웃한다.
        given(vacancyAlertSender.sendVacancyAlert(any())).willReturn(true);
    }

    /**
     * 명세 06 §2 6항 — 점유 종료·참여자 마감이 함께 일어나야 한다. 상태만 바꾸고 참여자를
     * 열어 두면 그 사람들이 영구히 다른 회의에 들어가지 못한다.
     */
    @Test
    @DisplayName("점유자의 소속이 끝나면 점유가 반납 처리되고 참여자도 마감된다.")
    void releasesOccupancyAndClosesParticipantsWhenOccupierMembershipEnds() {
        Long cohortId = fixture.createCohort("소속종료-점유");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member participant = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "소속종료-점유-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        participantService.add(roomId, participant.userId(), occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);

        occupancyCleanup.cleanUp(occupier.membershipId(), occupier.userId(), now());

        assertThat(occupancyStatus(occupancyId)).isEqualTo("RELEASED");
        assertThat(openParticipantRows(occupancyId)).isZero();
        assertThat(participantRows(occupancyId)).isEqualTo(2);
    }

    /**
     * <b>이 테스트가 이 기능의 존재 이유다.</b> 정리하지 않으면 열린 참여 행 하나가 그
     * 계정을 영구히 묶는다 — 점유 시작이 스스로를 참여자로 등록하므로(MR-27) 새 점유도
     * 함께 막힌다.
     */
    @Test
    @DisplayName("정리된 참여자는 곧바로 다른 회의실을 점유할 수 있다.")
    void cleanedUpParticipantCanOccupyAnotherRoom() {
        Long cohortId = fixture.createCohort("소속종료-재점유");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member participant = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "소속종료-재점유-1", 8);
        Long otherRoomId = fixture.createMeetingRoom(cohortId, "소속종료-재점유-2", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        participantService.add(roomId, participant.userId(), occupier.userId());

        occupancyCleanup.cleanUp(occupier.membershipId(), occupier.userId(), now());

        assertThatCode(() -> roomOccupancyService.start(otherRoomId, participant.userId()))
                .doesNotThrowAnyException();
    }

    /** 명세 06 §2 7항 — 남의 점유에 참여자로 있던 경우다. 점유 자체는 건드리지 않는다. */
    @Test
    @DisplayName("참여자의 소속이 끝나면 그 참여만 마감되고 점유는 유지된다.")
    void closesOnlyParticipationWhenParticipantMembershipEnds() {
        Long cohortId = fixture.createCohort("소속종료-참여만");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member participant = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "소속종료-참여만-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        participantService.add(roomId, participant.userId(), occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);

        occupancyCleanup.cleanUp(participant.membershipId(), participant.userId(), now());

        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
        assertThat(openParticipantRows(occupancyId)).isEqualTo(1);
    }

    /**
     * <b>가장 밟기 쉬운 함정이다</b> (명세 06 §2 2항). 이벤트가 도착하는 시점에는 멤버십이
     * 이미 {@code ENDED}이므로, 활성 소속으로 좁혀 조회하면 대상을 하나도 찾지 못하고
     * <b>활성 점유가 영구히 잔존한다.</b>
     */
    @Test
    @DisplayName("멤버십이 이미 종료된 뒤에 정리해도 대상을 찾는다.")
    void findsTargetEvenWhenMembershipAlreadyEnded() {
        Long cohortId = fixture.createCohort("소속종료-선종료");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "소속종료-선종료-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);

        endMembership(occupier.membershipId());

        assertThat(occupancyCleanup.cleanUp(
                occupier.membershipId(), occupier.userId(), now())).isTrue();
        assertThat(occupancyStatus(occupancyId)).isEqualTo("RELEASED");
    }

    /** 반납과 같은 규약이다 (명세 02 §3) — 사람이 빠져 방이 비었으므로 대기자에게 알린다. */
    @Test
    @DisplayName("정리로 비워진 방의 대기자에게 공실 알림이 발송된다.")
    void notifiesWaitingApplicantsWhenRoomIsFreedByCleanup() {
        Long cohortId = fixture.createCohort("소속종료-공실알림");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "소속종료-공실알림-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());

        occupancyCleanup.cleanUp(occupier.membershipId(), occupier.userId(), now());

        awaitUntil(() -> waitingAlertRows(roomId) == 0,
                "정리로 비워진 방의 공실 알림이 발송되지 않았습니다");
    }

    /**
     * 명세 06 §2 8단계 — 이 사람이 걸어 둔 대기 알림도 함께 지운다.
     *
     * <p><b>남기면 서비스를 떠난 사람에게 나중에 공실 알림이 발송된다.</b> 계정 삭제라면
     * 존재하지 않는 수신자에게 보내는 셈이다. 점유 반납과 같은 Transaction이라 공실
     * 이벤트가 커밋 후 발송될 때는 이미 행이 없다.</p>
     */
    @Test
    @DisplayName("소속이 끝나면 그 사람의 대기 알림도 삭제된다.")
    void discardsWaitingAlertsOfEndedMembership() {
        Long cohortId = fixture.createCohort("소속종료-알림삭제");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member waiter = fixture.createActiveStudent(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "소속종료-알림삭제-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, waiter.userId());

        occupancyCleanup.cleanUp(waiter.membershipId(), waiter.userId(), now());

        assertThat(waitingAlertRows(roomId)).isZero();
    }

    /**
     * 신청자의 소속이 끝난 뒤 <b>그 방이 비어도</b> 알림이 가지 않아야 한다. 위 테스트가
     * 행 삭제를 보는 것과 달리 여기는 발송 자체를 본다 — 삭제를 점유 반납과 다른
     * Transaction으로 옮기면 행은 지워지지만 발송은 나가는 상태가 되기 때문이다.
     */
    @Test
    @DisplayName("소속이 끝난 신청자는 방이 비어도 공실 알림을 받지 않는다.")
    void endedApplicantReceivesNoVacancyAlert() {
        Long cohortId = fixture.createCohort("소속종료-미발송");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member endedWaiter = fixture.createActiveStudent(cohortId);
        OccupancyTestFixture.Member survivingWaiter = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "소속종료-미발송-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        vacancyAlertService.request(roomId, null, endedWaiter.userId());
        vacancyAlertService.request(roomId, null, survivingWaiter.userId());

        occupancyCleanup.cleanUp(endedWaiter.membershipId(), endedWaiter.userId(), now());
        // 신청자가 아니라 점유자가 나가서 방이 빈다 — 공실 발송 경로를 실제로 태운다.
        occupancyCleanup.cleanUp(occupier.membershipId(), occupier.userId(), now());

        // 완료 신호는 생존 신청자에게 실제로 발송된 것 — 이것이 관찰되면 발송 경로가
        // 이 방에 대해 끝까지 돌았다는 뜻이다.
        ArgumentCaptor<VacancyNotice> captor = ArgumentCaptor.forClass(VacancyNotice.class);
        verify(vacancyAlertSender, timeout(5_000)).sendVacancyAlert(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(VacancyNotice::recipientUserId)
                .containsExactly(survivingWaiter.userId())
                .doesNotContain(endedWaiter.userId());
    }

    @Test
    @DisplayName("같은 정리를 두 번 해도 종료 사유와 시각이 바뀌지 않는다.")
    void isIdempotentAcrossRuns() {
        Long cohortId = fixture.createCohort("소속종료-멱등");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "소속종료-멱등-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);

        assertThat(occupancyCleanup.cleanUp(
                occupier.membershipId(), occupier.userId(), now())).isTrue();
        Object endedAt = occupancyEndedAt(occupancyId);

        assertThat(occupancyCleanup.cleanUp(
                occupier.membershipId(), occupier.userId(), now())).isFalse();
        assertThat(occupancyEndedAt(occupancyId)).isEqualTo(endedAt);
    }

    /**
     * <b>배선 자체를 확인한다.</b> 정리 규칙이 아무리 맞아도 이벤트가 리스너에 닿지 않으면
     * 아무 일도 일어나지 않는다 — 이 기능이 없던 상태가 정확히 그랬다.
     *
     * <p>{@code @Async}라 커밋 직후 다른 Thread에서 돌기 때문에 결과를 기다린다.</p>
     */
    @Test
    @DisplayName("소속을 종료하면 이벤트가 점유 정리까지 이어진다.")
    void membershipEndedEventReachesOccupancyCleanup() {
        Long cohortId = fixture.createCohort("소속종료-배선");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "소속종료-배선-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);

        membershipService.end(occupier.membershipId());

        awaitUntil(() -> "RELEASED".equals(occupancyStatusOrNull(occupancyId)),
                "소속 종료 이벤트가 점유 정리로 이어지지 않았습니다");
        assertThat(openParticipantRows(occupancyId)).isZero();
    }

    // ────────────────────────────── 정합성 스윕 ──────────────────────────────

    /**
     * <b>이벤트가 유실된 상태를 재현한다.</b> 소속을 SQL로 직접 끝내면 이벤트가 발행되지
     * 않으므로, 리스너가 손대지 않은 고아 상태가 그대로 남는다.
     */
    @Test
    @DisplayName("이벤트가 유실돼도 스윕이 점유를 정리한다.")
    void sweepCleansOrphanLeftByLostEvent() {
        Long cohortId = fixture.createCohort("스윕-점유");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "스윕-점유-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);
        endMembership(occupier.membershipId());

        assertThat(occupancySweep.sweep(200)).isGreaterThanOrEqualTo(1);

        assertThat(occupancyStatus(occupancyId)).isEqualTo("RELEASED");
        assertThat(openParticipantRows(occupancyId)).isZero();
    }

    /**
     * 열린 참여에서 출발하므로 <b>점유자가 아닌 참여자도 같은 순회에 걸린다</b> — 점유
     * 테이블을 따로 훑지 않는 근거다.
     */
    @Test
    @DisplayName("점유자가 아닌 참여자의 고아 행도 스윕이 마감한다.")
    void sweepClosesOrphanParticipationOfNonOccupier() {
        Long cohortId = fixture.createCohort("스윕-참여");
        Long nextCohortId = fixture.createCohort("스윕-참여-복귀");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member participant = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "스윕-참여-1", 8);
        Long otherRoomId = fixture.createMeetingRoom(nextCohortId, "스윕-참여-2", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        participantService.add(roomId, participant.userId(), occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);
        endMembership(participant.membershipId());

        occupancySweep.sweep(200);

        // 점유는 그대로다 — 끝난 것은 참여자의 소속뿐이다.
        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
        assertThat(openParticipantRows(occupancyId)).isEqualTo(1);

        // 종료된 소속은 LAB으로 복귀시키지 않는다. 같은 계정이 다른 활성 소속으로
        // 체크인한 뒤 다시 점유할 수 있으면 계정 단위 참여 잠금이 풀린 것이다.
        fixture.createActiveMember(nextCohortId, participant.userId());
        assertThatCode(() -> roomOccupancyService.start(otherRoomId, participant.userId()))
                .doesNotThrowAnyException();
    }

    /** 소속이 살아 있는 참여를 건드리면 사용 중인 회의가 끊긴다. */
    @Test
    @DisplayName("소속이 살아 있는 참여는 스윕이 건드리지 않는다.")
    void sweepLeavesActiveMembershipsUntouched() {
        Long cohortId = fixture.createCohort("스윕-정상");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "스윕-정상-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        Long occupancyId = activeOccupancyId(roomId);

        occupancySweep.sweep(200);

        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
        assertThat(openParticipantRows(occupancyId)).isEqualTo(1);
    }

    /**
     * 배치 크기를 1로 두어 커서 전진을 강제한다. 커서가 멈추면 앞쪽 배치만 반복해
     * 뒤쪽 대상에 영원히 닿지 못한다.
     */
    @Test
    @DisplayName("배치보다 대상이 많아도 커서가 끝까지 전진한다.")
    void sweepWalksBeyondSingleBatch() {
        Long cohortId = fixture.createCohort("스윕-커서");
        OccupancyTestFixture.Member first = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member second = fixture.createActiveMember(cohortId);
        Long firstRoomId = fixture.createMeetingRoom(cohortId, "스윕-커서-1", 8);
        Long secondRoomId = fixture.createMeetingRoom(cohortId, "스윕-커서-2", 8);

        roomOccupancyService.start(firstRoomId, first.userId());
        roomOccupancyService.start(secondRoomId, second.userId());
        Long firstOccupancyId = activeOccupancyId(firstRoomId);
        Long secondOccupancyId = activeOccupancyId(secondRoomId);
        endMembership(first.membershipId());
        endMembership(second.membershipId());

        occupancySweep.sweep(1);

        assertThat(occupancyStatus(firstOccupancyId)).isEqualTo("RELEASED");
        assertThat(occupancyStatus(secondOccupancyId)).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("스윕을 두 번 돌려도 두 번째는 대상이 없다.")
    void sweepIsIdempotentAcrossRuns() {
        Long cohortId = fixture.createCohort("스윕-멱등");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "스윕-멱등-1", 8);

        roomOccupancyService.start(roomId, occupier.userId());
        endMembership(occupier.membershipId());

        assertThat(occupancySweep.sweep(200)).isGreaterThanOrEqualTo(1);
        assertThat(occupancySweep.sweep(200)).isZero();
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void awaitUntil(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError(message);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /** 점유를 건드리지 않고 소속만 끝낸다. "이미 ENDED인 상태로 도착"을 재현할 때 쓴다. */
    private void endMembership(Long membershipId) {
        jdbcTemplate.update("""
                UPDATE learning_service.cohort_memberships
                   SET status = 'ENDED', ended_at = now()
                 WHERE id = ?
                """, membershipId);
    }

    private String occupancyStatus(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM learning_service.room_occupancies WHERE id = ?
                """, String.class, occupancyId);
    }

    /** 정리 도중에는 값이 바뀌는 중일 수 있어 단건 조회 대신 목록으로 읽는다. */
    private String occupancyStatusOrNull(Long occupancyId) {
        return jdbcTemplate.queryForList("""
                        SELECT status FROM learning_service.room_occupancies WHERE id = ?
                        """, String.class, occupancyId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Object occupancyEndedAt(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT ended_at FROM learning_service.room_occupancies WHERE id = ?
                """, Object.class, occupancyId);
    }

    private Long activeOccupancyId(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, Long.class, spaceId);
    }

    private int openParticipantRows(Long occupancyId) {
        return count("""
                SELECT count(*) FROM learning_service.occupancy_participants
                 WHERE occupancy_id = ? AND left_at IS NULL
                """, occupancyId);
    }

    private int participantRows(Long occupancyId) {
        return count("""
                SELECT count(*) FROM learning_service.occupancy_participants
                 WHERE occupancy_id = ?
                """, occupancyId);
    }

    private int waitingAlertRows(Long spaceId) {
        return count("""
                SELECT count(*) FROM learning_service.vacancy_alerts
                 WHERE space_id = ? AND notified_at IS NULL
                """, spaceId);
    }

    private int count(String sql, Long id) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count == null ? 0 : count;
    }
}
