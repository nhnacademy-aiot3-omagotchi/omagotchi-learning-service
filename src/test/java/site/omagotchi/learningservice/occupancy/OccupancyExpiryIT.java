package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.learningservice.occupancy.application.OccupancyExpiryReminder;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * 만료된 점유의 정리 (MR-32).
 *
 * <p>만료 스케줄러(#9)가 아직 없어 점유 시작이 {@code expireStale*}로 정리를 대행한다.
 * 그 경로가 점유 행만 EXPIRED로 바꾸고 참여자를 열어 두면
 * {@code uq_occupancy_participants_one_active}가 계정 기준이라 <b>그 사람이 다시는 어떤
 * 회의에도 들어갈 수 없다.</b> 점유 시작이 스스로를 참여자로 등록하므로(MR-27) 새 점유도
 * 함께 막힌다.</p>
 *
 * <p>실제 PostgreSQL이 있어야 의미가 있다 — 막는 것이 애플리케이션 조건이 아니라
 * 부분 유니크 인덱스다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class OccupancyExpiryIT {

    @Autowired
    OccupancyTestFixture fixture;

    @Autowired
    RoomOccupancyService roomOccupancyService;

    @Autowired
    RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    @Autowired
    OccupancyExpiryReminder occupancyExpiryReminder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RoomOccupancyRepository occupancyRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    OccupancyParticipantRepository participantRepository;

    /**
     * 만료 뒤 다른 방을 잡는 것은 정상 흐름이다. 2시간이 지나 점유가 끝났으면
     * 그 사람은 아무 제약 없이 새로 점유할 수 있어야 한다.
     */
    @Test
    @DisplayName("점유가 만료된 사용자는 다른 회의실을 새로 점유할 수 있다.")
    void expiredUserCanOccupyAnotherRoom() {
        Long cohortId = fixture.createCohort("만료-재점유");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long firstRoomId = fixture.createMeetingRoom(cohortId, "만료-재점유-1", 8);
        Long secondRoomId = fixture.createMeetingRoom(cohortId, "만료-재점유-2", 8);

        roomOccupancyService.start(firstRoomId, member.userId());
        expire(firstRoomId);

        assertThatCode(() -> roomOccupancyService.start(secondRoomId, member.userId()))
                .doesNotThrowAnyException();
    }

    /**
     * 참여 이력은 남기되 구간은 닫아야 한다 (MR-32). 행을 지우면 이력이 사라지고,
     * 열어 두면 그 계정이 영구히 다른 회의에 참여할 수 없다.
     */
    @Test
    @DisplayName("만료 정리는 참여자 행을 지우지 않고 left_at만 마감한다.")
    void expiryClosesParticipantsWithoutDeletingRows() {
        Long cohortId = fixture.createCohort("만료-참여자마감");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-참여자마감-1", 8);
        Long otherRoomId = fixture.createMeetingRoom(cohortId, "만료-참여자마감-2", 8);

        roomOccupancyService.start(roomId, member.userId());
        expire(roomId);
        roomOccupancyService.start(otherRoomId, member.userId());

        assertThat(participantRows(roomId)).isEqualTo(1);
        assertThat(openParticipantRows(roomId)).isZero();
    }

    /**
     * {@code left_at}은 정리 시각이 아니라 점유의 만료 시각이다. 점유 행의
     * {@code ended_at}도 같은 규약이며({@code expires_at}), 다르게 찍으면 "회의가 끝난
     * 시각"이 두 테이블에서 어긋나 참여 시간 집계가 틀어진다.
     */
    @Test
    @DisplayName("참여자 마감 시각은 점유의 만료 시각과 같다.")
    void participantClosedAtMatchesOccupancyExpiryTime() {
        Long cohortId = fixture.createCohort("만료-마감시각");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-마감시각-1", 8);
        Long otherRoomId = fixture.createMeetingRoom(cohortId, "만료-마감시각-2", 8);

        roomOccupancyService.start(roomId, member.userId());
        expire(roomId);
        roomOccupancyService.start(otherRoomId, member.userId());

        assertThat(participantLeftAt(roomId)).isEqualTo(occupancyEndedAt(roomId));
    }

    /**
     * 스케줄러의 존재 이유 (#9).
     *
     * <p>{@code expireStale*}는 누군가 그 공간을 점유하려 하거나 그 계정이 새로 점유할 때만
     * 돈다. 아무도 오지 않는 방은 만료돼도 ACTIVE로 남아 목록에 "사용 중"으로 뜨고
     * 참여자도 열린 채다 — 그 상태를 이 경로만이 해소한다.</p>
     */
    @Test
    @DisplayName("아무도 찾지 않는 방도 스케줄러가 정리한다.")
    void schedulerExpiresUnvisitedRoom() {
        Long cohortId = fixture.createCohort("만료-스케줄러");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-스케줄러-1", 8);

        roomOccupancyService.start(roomId, member.userId());
        expire(roomId);

        roomOccupancyLifecycleService.expireAll();

        assertThat(activeOccupancyRows(roomId)).isZero();
        assertThat(openParticipantRows(roomId)).isZero();
        assertThat(participantRows(roomId)).isEqualTo(1);
    }

    /**
     * 정리는 재실행이 안전해야 한다 — 실패한 주기를 다음 주기가 그대로 다시 처리한다.
     *
     * <p>첫 실행의 건수를 정확히 못 박지 않는 것은 {@code expireAll}이 전역이기 때문이다.
     * 통합 테스트는 트랜잭션 롤백 없이 컨테이너를 공유하므로 앞선 테스트가 남긴 만료 행이
     * 함께 정리될 수 있다. 고정할 것은 "두 번째 실행에 남는 것이 없다"이다.</p>
     */
    @Test
    @DisplayName("정리를 두 번 돌려도 두 번째는 대상이 없다.")
    void secondExpiryRunFindsNoCandidates() {
        Long cohortId = fixture.createCohort("만료-멱등");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-멱등-1", 8);

        roomOccupancyService.start(roomId, member.userId());
        expire(roomId);

        assertThat(roomOccupancyLifecycleService.expireAll()).isGreaterThanOrEqualTo(1);
        assertThat(roomOccupancyLifecycleService.expireAll()).isZero();
        assertThat(activeOccupancyRows(roomId)).isZero();
    }

    /** 아직 만료되지 않은 점유를 쓸어가면 사용 중인 회의가 끊긴다. */
    @Test
    @DisplayName("만료되지 않은 점유는 건드리지 않는다.")
    void doesNotTouchNonExpiredOccupancy() {
        Long cohortId = fixture.createCohort("만료-미도래");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-미도래-1", 8);

        roomOccupancyService.start(roomId, member.userId());

        roomOccupancyLifecycleService.expireAll();

        assertThat(activeOccupancyRows(roomId)).isEqualTo(1);
        assertThat(openParticipantRows(roomId)).isEqualTo(1);
    }

    @Test
    @DisplayName("임박 후보 조회는 10분 경계를 포함하고 만료·종료·기발송 점유를 제외한다.")
    void expiringSoonQueryUsesExactReminderConditions() {
        Long cohortId = fixture.createCohort("임박-조회조건");
        OffsetDateTime now = OffsetDateTime.now();

        Long withinId = startAt(cohortId, "임박-5분", now.plusMinutes(5), null);
        Long boundaryId = startAt(cohortId, "임박-10분", now.plusMinutes(10), null);
        Long tooEarlyId = startAt(cohortId, "임박-11분", now.plusMinutes(11), null);
        Long exactExpiryId = startAt(cohortId, "임박-정각만료", now, null);
        Long pastId = startAt(cohortId, "임박-과거", now.minusSeconds(1), null);
        Long remindedId = startAt(cohortId, "임박-기발송", now.plusMinutes(5), now.minusMinutes(1));
        Long releasedId = startAt(cohortId, "임박-종료", now.plusMinutes(5), null);
        jdbcTemplate.update("""
                UPDATE learning_service.room_occupancies
                   SET status = 'RELEASED', ended_at = ?
                 WHERE id = ?
                """, now, releasedId);
        jdbcTemplate.update("""
                UPDATE learning_service.occupancy_participants
                   SET left_at = ?
                 WHERE occupancy_id = ?
                   AND left_at IS NULL
                """, now, releasedId);

        Set<Long> ownIds = Set.of(
                withinId, boundaryId, tooEarlyId, exactExpiryId, pastId, remindedId, releasedId);
        List<Long> found = occupancyRepository.findExpiringSoon(now, now.plusMinutes(10)).stream()
                .map(RoomOccupancyRepository.ExpiringOccupancy::occupancyId)
                .filter(ownIds::contains)
                .toList();

        assertThat(found).containsExactly(withinId, boundaryId);
    }

    @Test
    @DisplayName("임박 알림 성공은 DB에 기록되고 같은 후보를 다시 처리해도 중복 발송하지 않는다.")
    void successfulReminderIsPersistedAndNotSentTwice() {
        Long cohortId = fixture.createCohort("임박-성공중복방지");
        OffsetDateTime now = OffsetDateTime.now();
        Long occupancyId = startAt(cohortId, "임박-성공중복방지-1", now.plusMinutes(5), null);
        RoomOccupancyRepository.ExpiringOccupancy candidate = expiringCandidate(occupancyId);
        AtomicInteger attempts = new AtomicInteger();
        OccupancyReminderSender sender = reminder -> attempts.incrementAndGet();

        assertThat(occupancyExpiryReminder.send(candidate, sender)).isTrue();
        assertThat(reminderSentAt(occupancyId)).isNotNull();

        assertThat(occupancyExpiryReminder.send(candidate, sender)).isFalse();
        assertThat(attempts).hasValue(1);
        assertThat(expiringOccupancyIds()).doesNotContain(occupancyId);
    }

    @Test
    @DisplayName("임박 알림 실패는 기록을 소진하지 않아 다음 실행에서 재시도할 수 있다.")
    void failedReminderRemainsEligibleForRetry() {
        Long cohortId = fixture.createCohort("임박-실패재시도");
        OffsetDateTime now = OffsetDateTime.now();
        Long occupancyId = startAt(cohortId, "임박-실패재시도-1", now.plusMinutes(5), null);
        RoomOccupancyRepository.ExpiringOccupancy candidate = expiringCandidate(occupancyId);

        assertThatThrownBy(() -> occupancyExpiryReminder.send(candidate, reminder -> {
            throw new IllegalStateException("강제 발송 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(reminderSentAt(occupancyId)).isNull();
        assertThat(expiringOccupancyIds()).contains(occupancyId);
        assertThat(occupancyExpiryReminder.send(candidate, reminder -> { })).isTrue();
        assertThat(reminderSentAt(occupancyId)).isNotNull();
    }

    @Test
    @DisplayName("임박 알림 트랜잭션이 실패해도 이후 만료의 EXPIRED 전이는 커밋된다.")
    void reminderFailureDoesNotRollbackLaterExpiry() {
        Long cohortId = fixture.createCohort("임박실패-만료격리");
        OffsetDateTime now = OffsetDateTime.now();
        Long occupancyId = startAt(cohortId, "임박실패-만료격리-1", now.plusMinutes(5), null);
        RoomOccupancyRepository.ExpiringOccupancy candidate = expiringCandidate(occupancyId);
        Long roomId = occupancySpaceId(occupancyId);

        assertThatThrownBy(() -> occupancyExpiryReminder.send(candidate, reminder -> {
            throw new IllegalStateException("강제 발송 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(reminderSentAt(occupancyId)).isNull();

        expire(roomId);
        roomOccupancyLifecycleService.expireAll();

        assertThat(occupancyStatus(occupancyId)).isEqualTo("EXPIRED");
        assertThat(occupancyEndedAt(roomId)).isNotNull();
    }

    /**
     * <b>건별 트랜잭션의 Commit·Rollback 검증</b> (명세서 03 §4, 10-backend-code-structure §4).
     *
     * <p>한 건이 터져도 나머지는 이번 주기에 커밋돼야 한다. 한 트랜잭션으로 묶여 있으면
     * 실패한 건이 성공한 건까지 되돌려, 뒤 순번 점유들이 다음 주기까지 — 그 주기에도 같은
     * 건이 먼저 터지면 영원히 — 방치된다.</p>
     *
     * <p>실패한 건 자체는 ACTIVE로 남아야 한다. 그래야 다음 주기가 다시 집어 간다.</p>
     */
    @Test
    @DisplayName("한 건이 실패해도 나머지 점유는 정리되고 커밋된다.")
    void oneFailureDoesNotBlockOtherExpiries() {
        Long cohortId = fixture.createCohort("만료-건별격리");
        OccupancyTestFixture.Member first = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member second = fixture.createActiveMember(cohortId);
        Long failingRoomId = fixture.createMeetingRoom(cohortId, "만료-건별격리-1", 8);
        Long healthyRoomId = fixture.createMeetingRoom(cohortId, "만료-건별격리-2", 8);

        roomOccupancyService.start(failingRoomId, first.userId());
        roomOccupancyService.start(healthyRoomId, second.userId());
        expire(failingRoomId);
        expire(healthyRoomId);

        Long failingOccupancyId = activeOccupancyId(failingRoomId);
        doThrow(new IllegalStateException("참여자 마감 실패"))
                .when(participantRepository)
                .closeAllActiveByOccupancyId(eq(failingOccupancyId), any());

        roomOccupancyLifecycleService.expireAll();

        // 정상 건은 커밋됐다
        assertThat(activeOccupancyRows(healthyRoomId)).isZero();
        assertThat(openParticipantRows(healthyRoomId)).isZero();

        // 실패 건은 롤백돼 ACTIVE로 남았다 — 다음 주기가 다시 집어 간다
        assertThat(activeOccupancyRows(failingRoomId)).isEqualTo(1);
    }

    /** 이미 사용 중인 회의실을 뒤늦게 잡을 때도 같은 정리가 일어나야 한다. */
    @Test
    @DisplayName("공간 기준 정리도 참여자를 함께 마감한다.")
    void spaceScopedExpiryAlsoClosesParticipants() {
        Long cohortId = fixture.createCohort("만료-공간기준");
        OccupancyTestFixture.Member first = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member second = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-공간기준-1", 8);

        roomOccupancyService.start(roomId, first.userId());
        expire(roomId);

        roomOccupancyService.start(roomId, second.userId());

        assertThat(openParticipantRows(roomId)).isEqualTo(1);
    }

    // ────────────────────────────── 반납·연장과의 경합 ──────────────────────────────

    /**
     * {@code expireStaleBySpaceId}·{@code expireStaleByUserId}는 후보 식별과 전이를
     * {@code RoomOccupancyRepository#expire}(단건 조건부 UPDATE)로 나눠 처리한다.
     * 후보를 찾아둔 시각(스캔 {@code now}) 기준으로는 만료로 보였어도, 그 사이 다른 요청이
     * 반납을 커밋하면 전이 시도는 실패해야 한다 — 벌크 UPDATE로 처리하고 조회 결과를 그대로
     * 반환하던 예전 구현은 이 경합을 놓쳐, 이미 정상적으로 반납된 점유를 "정리했다"고
     * 잘못 보고했다(참여자 재마감·중복 공실 알림으로 이어진다).
     */
    @Test
    @DisplayName("만료 후보로 식별된 뒤 반납되면 단건 전이는 실패한다.")
    void transitionFailsWhenReleasedAfterBeingIdentifiedAsCandidate() {
        Long cohortId = fixture.createCohort("만료-반납경합");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-반납경합-1", 8);

        roomOccupancyService.start(roomId, member.userId());
        expire(roomId);   // status는 ACTIVE인 채로 expires_at만 과거로 — "만료 후보"로 보일 상태
        Long occupancyId = activeOccupancyId(roomId);
        OffsetDateTime scanNow = OffsetDateTime.now();

        // 후보로 식별된 뒤, 다른 요청이 반납을 먼저 커밋한다.
        roomOccupancyLifecycleService.release(roomId, member.userId());

        // 단건 전이 시도 — 반납으로 status가 이미 ACTIVE가 아니므로 실패해야 한다.
        Boolean transitioned = transactionTemplate.execute(
                status -> occupancyRepository.expire(occupancyId, scanNow));
        assertThat(transitioned).isFalse();

        // 반납이 이미 올바르게 정리했다 — 실패한 전이가 그 결과를 다시 건드리면 안 된다.
        assertThat(activeOccupancyRows(roomId)).isZero();
        assertThat(openParticipantRows(roomId)).isZero();
    }

    /**
     * 연장 쪽도 같은 방어가 필요하다 — 실패하지 않으면 지금 회의 중인 사람들의 참여자
     * 행이 닫히고, 여전히 쓰고 있는 방에 대해 "비었다"는 공실 알림이 잘못 나간다.
     */
    @Test
    @DisplayName("만료 후보로 식별된 뒤 연장되면 단건 전이는 실패한다.")
    void transitionFailsWhenExtendedAfterBeingIdentifiedAsCandidate() {
        Long cohortId = fixture.createCohort("만료-연장경합");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-연장경합-1", 8);

        roomOccupancyService.start(roomId, member.userId());
        Long occupancyId = activeOccupancyId(roomId);

        // 곧 만료될 것처럼 보이게 하되(연장 가능 창 안), 아직 실제로 만료되지는 않게 둔다.
        jdbcTemplate.update("""
                UPDATE learning_service.room_occupancies
                   SET expires_at = now() + interval '5 seconds'
                 WHERE id = ?
                """, occupancyId);
        // "이 시각 기준으로는 이미 지났다"고 스캔이 오인했을 now — 아래 연장이 실제로
        // 성공하는 실제 시각보다 뒤다.
        OffsetDateTime scanNow = OffsetDateTime.now().plusSeconds(10);

        // 후보로 식별된 뒤, 다른 요청이 연장을 먼저 커밋한다.
        roomOccupancyLifecycleService.extend(roomId, member.userId());

        // 단건 전이 시도 — 연장으로 expires_at이 미래로 밀려 스캔 시점 now로도 조건을 만족하지 않는다.
        Boolean transitioned = transactionTemplate.execute(
                status -> occupancyRepository.expire(occupancyId, scanNow));
        assertThat(transitioned).isFalse();

        // 연장된 점유는 여전히 사용 중이어야 한다 — 참여자도 그대로 열려 있어야 한다.
        assertThat(activeOccupancyRows(roomId)).isEqualTo(1);
        assertThat(openParticipantRows(roomId)).isEqualTo(1);
    }

    /**
     * 2시간이 지난 상태를 만든다. 실제 시간을 기다릴 수 없으므로 행을 과거로 민다.
     *
     * <p>참여자의 {@code joined_at}도 함께 미는 것이 중요하다. 점유만 과거로 보내면
     * {@code joined_at}이 {@code expires_at}보다 뒤인 상태가 되는데, 실제로는 활성 점유에만
     * 참여할 수 있어 그런 행이 존재할 수 없다. 그대로 두면 마감 시각이
     * {@code ck_occupancy_participants_period}에 걸려, 프로덕션에 없는 이유로 테스트가 깨진다.</p>
     */
    private void expire(Long spaceId) {
        jdbcTemplate.update("""
                UPDATE learning_service.occupancy_participants
                   SET joined_at = now() - interval '3 hours'
                 WHERE occupancy_id IN (
                       SELECT id FROM learning_service.room_occupancies
                        WHERE space_id = ? AND status = 'ACTIVE')
                """, spaceId);
        jdbcTemplate.update("""
                UPDATE learning_service.room_occupancies
                   SET started_at = now() - interval '3 hours',
                       expires_at = now() - interval '1 minute'
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, spaceId);
    }

    private Long startAt(
            Long cohortId, String roomName, OffsetDateTime expiresAt, OffsetDateTime reminderSentAt) {
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, roomName, 8);
        roomOccupancyService.start(roomId, member.userId());
        Long occupancyId = activeOccupancyId(roomId);
        OffsetDateTime startedAt = expiresAt.minusHours(2);
        jdbcTemplate.update("""
                UPDATE learning_service.occupancy_participants
                   SET joined_at = ?
                 WHERE occupancy_id = ?
                """, startedAt, occupancyId);
        jdbcTemplate.update("""
                UPDATE learning_service.room_occupancies
                   SET started_at = ?, expires_at = ?, reminder_sent_at = ?
                 WHERE id = ?
                """, startedAt, expiresAt, reminderSentAt, occupancyId);
        return occupancyId;
    }

    private OffsetDateTime occupancyEndedAt(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT ended_at FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'EXPIRED'
                """, OffsetDateTime.class, spaceId);
    }

    private RoomOccupancyRepository.ExpiringOccupancy expiringCandidate(Long occupancyId) {
        return occupancyRepository.findExpiringSoon(
                        OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(10)).stream()
                .filter(candidate -> candidate.occupancyId().equals(occupancyId))
                .findFirst()
                .orElseThrow();
    }

    private List<Long> expiringOccupancyIds() {
        OffsetDateTime now = OffsetDateTime.now();
        return occupancyRepository.findExpiringSoon(now, now.plusMinutes(10)).stream()
                .map(RoomOccupancyRepository.ExpiringOccupancy::occupancyId)
                .toList();
    }

    private OffsetDateTime reminderSentAt(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT reminder_sent_at FROM learning_service.room_occupancies
                 WHERE id = ?
                """, OffsetDateTime.class, occupancyId);
    }

    private Long occupancySpaceId(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT space_id FROM learning_service.room_occupancies
                 WHERE id = ?
                """, Long.class, occupancyId);
    }

    private String occupancyStatus(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM learning_service.room_occupancies
                 WHERE id = ?
                """, String.class, occupancyId);
    }

    private OffsetDateTime participantLeftAt(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT p.left_at
                  FROM learning_service.occupancy_participants p
                  JOIN learning_service.room_occupancies o ON o.id = p.occupancy_id
                 WHERE o.space_id = ? AND o.status = 'EXPIRED'
                """, OffsetDateTime.class, spaceId);
    }

    private Long activeOccupancyId(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, Long.class, spaceId);
    }

    private int activeOccupancyRows(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }

    private int participantRows(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.occupancy_participants p
                  JOIN learning_service.room_occupancies o ON o.id = p.occupancy_id
                 WHERE o.space_id = ?
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }

    private int openParticipantRows(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.occupancy_participants p
                  JOIN learning_service.room_occupancies o ON o.id = p.occupancy_id
                 WHERE o.space_id = ? AND p.left_at IS NULL
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }
}
