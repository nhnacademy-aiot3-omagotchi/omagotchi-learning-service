package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
    JdbcTemplate jdbcTemplate;

    /**
     * 만료 뒤 다른 방을 잡는 것은 정상 흐름이다. 2시간이 지나 점유가 끝났으면
     * 그 사람은 아무 제약 없이 새로 점유할 수 있어야 한다.
     */
    @Test
    @DisplayName("점유가 만료된 사용자는 다른 회의실을 새로 점유할 수 있다.")
    void test1() {
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
    void test2() {
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
    void test3() {
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
    void test5() {
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
    void test6() {
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
    void test7() {
        Long cohortId = fixture.createCohort("만료-미도래");
        OccupancyTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-미도래-1", 8);

        roomOccupancyService.start(roomId, member.userId());

        roomOccupancyLifecycleService.expireAll();

        assertThat(activeOccupancyRows(roomId)).isEqualTo(1);
        assertThat(openParticipantRows(roomId)).isEqualTo(1);
    }

    /** 이미 사용 중인 회의실을 뒤늦게 잡을 때도 같은 정리가 일어나야 한다. */
    @Test
    @DisplayName("공간 기준 정리도 참여자를 함께 마감한다.")
    void test4() {
        Long cohortId = fixture.createCohort("만료-공간기준");
        OccupancyTestFixture.Member first = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member second = fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "만료-공간기준-1", 8);

        roomOccupancyService.start(roomId, first.userId());
        expire(roomId);

        roomOccupancyService.start(roomId, second.userId());

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

    private OffsetDateTime occupancyEndedAt(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT ended_at FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'EXPIRED'
                """, OffsetDateTime.class, spaceId);
    }

    private OffsetDateTime participantLeftAt(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT p.left_at
                  FROM learning_service.occupancy_participants p
                  JOIN learning_service.room_occupancies o ON o.id = p.occupancy_id
                 WHERE o.space_id = ? AND o.status = 'EXPIRED'
                """, OffsetDateTime.class, spaceId);
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
