package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 점유·참여자의 동시성 방어 (MR-08, MR-10, MR-28, MR-35).
 *
 * <p>단위 테스트로는 검증할 수 없는 것만 여기 둔다. 선검사는 동시 요청을 막지 못하고
 * 부분 유니크와 행 락이 최종 방어선이므로, 실제 PostgreSQL이 있어야 의미가 있다.</p>
 *
 * <p>기수·공간을 테스트마다 새로 만드는 것이 중요하다. 통합 테스트는 트랜잭션 롤백 없이
 * 같은 컨테이너를 공유해서, 재사용하면 앞선 테스트가 남긴 활성 점유가 유니크에 걸려
 * 엉뚱한 곳에서 실패한다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class OccupancyConcurrencyIT {

    private static final int THREADS = 20;

    @Autowired
    OccupancyTestFixture fixture;

    @Autowired
    RoomOccupancyService roomOccupancyService;

    @Autowired
    OccupancyParticipantService occupancyParticipantService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * 락은 1차 필터일 뿐이다. 동시 요청 둘이 나란히 "활성 점유 없음"을 볼 수 있고,
     * 그때 실제로 막는 것은 {@code uq_room_occupancies_one_active_per_space}다.
     */
    @Test
    @DisplayName("같은 회의실에 20명이 동시에 점유를 요청하면 1명만 성공한다.")
    void test1() throws Exception {
        Long cohortId = fixture.createCohort("동시점유 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "동시점유 회의실", 30);

        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            users.add(fixture.createActiveMember(cohortId).userId());
        }

        Result result = runConcurrently(users, userId -> roomOccupancyService.start(spaceId, userId));

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failure()).isEqualTo(THREADS - 1);
        assertThat(activeOccupancies(spaceId)).isEqualTo(1);

        // 실패한 요청은 참여자 행도 남기지 않아야 한다 — 점유 INSERT와 같은 트랜잭션이다.
        assertThat(activeParticipants(spaceId)).isEqualTo(1);
    }

    /**
     * 실패가 전부 409여야 한다. 유니크 위반이 {@code ErrorCode}로 변환되지 않으면
     * 500으로 새어 나가는데, 성공 1건만 세는 검증으로는 그것을 잡지 못한다.
     */
    @Test
    @DisplayName("동시 점유에서 밀린 요청은 모두 사용 중(409)으로 응답한다.")
    void test2() throws Exception {
        Long cohortId = fixture.createCohort("409 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "409 회의실", 30);

        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            users.add(fixture.createActiveMember(cohortId).userId());
        }

        Result result = runConcurrently(users, userId -> roomOccupancyService.start(spaceId, userId));

        assertThat(result.errors()).hasSize(THREADS - 1);
        assertThat(result.errors()).allSatisfy(thrown -> assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(OccupancyErrorCode.ROOM_ALREADY_OCCUPIED));
    }

    /**
     * 배타가 멤버십이 아니라 계정 기준인 것을 고정한다 (MR-10). 멤버십 기준이었다면
     * 두 멤버십의 값이 달라 유니크를 통과해 방을 둘 잡을 수 있다.
     */
    @Test
    @DisplayName("다기수 담당자는 서로 다른 멤버십으로도 회의실을 둘 잡을 수 없다.")
    void test3() {
        Long firstCohortId = fixture.createCohort("다기수 3기");
        Long secondCohortId = fixture.createCohort("다기수 4기");
        UUID managerUserId = UUID.randomUUID();
        fixture.createActiveMember(firstCohortId, managerUserId);
        fixture.createActiveMember(secondCohortId, managerUserId);

        Long firstRoomId = fixture.createMeetingRoom(firstCohortId, "다기수 회의실 A", 8);
        Long secondRoomId = fixture.createMeetingRoom(secondCohortId, "다기수 회의실 B", 8);

        roomOccupancyService.start(firstRoomId, managerUserId);

        Throwable thrown = catchThrowable(
                () -> roomOccupancyService.start(secondRoomId, managerUserId));

        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OccupancyErrorCode.ALREADY_OCCUPYING);
        assertThat(activeOccupancies(secondRoomId)).isZero();
    }

    /** 기수 쿼터는 없다 — 회의실은 순수 선착순으로 배분된다 (MR-35). */
    @Test
    @DisplayName("서로 다른 기수가 서로 다른 회의실을 동시에 점유할 수 있다.")
    void test4() throws Exception {
        Long firstCohortId = fixture.createCohort("MR-35 3기");
        Long secondCohortId = fixture.createCohort("MR-35 4기");
        UUID firstUserId = fixture.createActiveMember(firstCohortId).userId();
        UUID secondUserId = fixture.createActiveMember(secondCohortId).userId();

        Long firstRoomId = fixture.createMeetingRoom(firstCohortId, "MR-35 회의실 A", 8);
        Long secondRoomId = fixture.createMeetingRoom(secondCohortId, "MR-35 회의실 B", 8);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = pool.submit(() -> {
                start.await();
                return catchThrowable(() -> roomOccupancyService.start(firstRoomId, firstUserId));
            });
            Future<Throwable> second = pool.submit(() -> {
                start.await();
                return catchThrowable(() -> roomOccupancyService.start(secondRoomId, secondUserId));
            });
            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS)).isNull();
            assertThat(second.get(30, TimeUnit.SECONDS)).isNull();
        } finally {
            pool.shutdownNow();
        }

        assertThat(activeOccupancies(firstRoomId)).isEqualTo(1);
        assertThat(activeOccupancies(secondRoomId)).isEqualTo(1);
    }

    /**
     * 정원은 "최대 N행"이라 유니크 인덱스로 표현할 수 없다. 점유 행 락 안에서 세는 것이
     * 유일한 방어선이라, 락이 빠지면 이 테스트만 잡아낸다.
     */
    @Test
    @DisplayName("잔여 1석에 여러 명이 동시에 추가돼도 정원을 넘지 않는다.")
    void test5() throws Exception {
        Long cohortId = fixture.createCohort("정원 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "정원 회의실", 2);

        UUID occupierUserId = fixture.createActiveMember(cohortId).userId();
        roomOccupancyService.start(spaceId, occupierUserId);   // 점유자가 1석을 차지한다

        List<UUID> candidates = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            candidates.add(fixture.createActiveMember(cohortId).userId());
        }

        Result result = runConcurrently(candidates,
                targetUserId -> occupancyParticipantService.add(spaceId, targetUserId, occupierUserId));

        assertThat(result.success()).isEqualTo(1);
        assertThat(activeParticipants(spaceId)).isEqualTo(2);
        assertThat(result.errors()).allSatisfy(thrown -> assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(OccupancyErrorCode.CAPACITY_EXCEEDED));
    }

    /**
     * DB가 막지 않는 유일한 제약이다 (MR-33). 스키마 v1.3에서 {@code cohort_id} 컬럼과
     * 복합 FK를 제거했으므로 애플리케이션 검증이 뚫리면 그대로 저장된다.
     */
    @Test
    @DisplayName("타 기수 사용자는 참여자로 추가되지 않는다.")
    void test6() {
        Long occupierCohortId = fixture.createCohort("MR-33 3기");
        Long otherCohortId = fixture.createCohort("MR-33 4기");
        Long spaceId = fixture.createMeetingRoom(occupierCohortId, "MR-33 회의실", 8);

        UUID occupierUserId = fixture.createActiveMember(occupierCohortId).userId();
        UUID otherCohortUserId = fixture.createActiveMember(otherCohortId).userId();

        roomOccupancyService.start(spaceId, occupierUserId);

        Throwable thrown = catchThrowable(() ->
                occupancyParticipantService.add(spaceId, otherCohortUserId, occupierUserId));

        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OccupancyErrorCode.DIFFERENT_COHORT);
        assertThat(activeParticipants(spaceId)).isEqualTo(1);
    }

    /**
     * 행 재사용을 실제 DB에서 확인한다 (결정 #30). 새 행을 만들면
     * {@code uq_occupancy_participants_pair} 위반이라 여기서 409가 터진다.
     */
    @Test
    @DisplayName("이탈 후 재합류하면 새 행 없이 기존 행이 재사용된다.")
    void test7() {
        Long cohortId = fixture.createCohort("재합류 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "재합류 회의실", 8);

        UUID occupierUserId = fixture.createActiveMember(cohortId).userId();
        UUID participantUserId = fixture.createActiveMember(cohortId).userId();

        roomOccupancyService.start(spaceId, occupierUserId);
        occupancyParticipantService.add(spaceId, participantUserId, occupierUserId);
        occupancyParticipantService.remove(spaceId, participantUserId, participantUserId);
        occupancyParticipantService.add(spaceId, participantUserId, occupierUserId);

        assertThat(participantRows(spaceId, participantUserId)).isEqualTo(1);
        assertThat(activeParticipants(spaceId)).isEqualTo(2);
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    /**
     * 모든 스레드를 래치로 묶어 동시에 출발시킨다. 순차 실행하면 선검사에서 전부 걸려
     * 유니크·락 경로를 지나지 않으므로, 이 테스트가 검증하려는 것을 놓친다.
     */
    private Result runConcurrently(List<UUID> users, Action action) throws Exception {
        CountDownLatch ready = new CountDownLatch(users.size());
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(users.size());
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (UUID userId : users) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    Throwable thrown = catchThrowable(() -> action.run(userId));
                    if (thrown == null) {
                        success.incrementAndGet();
                    }
                    return thrown;
                }));
            }

            assertThat(ready.await(30, TimeUnit.SECONDS))
                    .as("모든 스레드가 출발선에 도달해야 동시성 경로를 검증할 수 있다")
                    .isTrue();
            start.countDown();

            for (Future<Throwable> future : futures) {
                Throwable thrown = future.get(60, TimeUnit.SECONDS);
                if (thrown != null) {
                    errors.add(thrown);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return new Result(success.get(), errors);
    }

    private int activeOccupancies(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }

    private int activeParticipants(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.occupancy_participants p
                  JOIN learning_service.room_occupancies o ON o.id = p.occupancy_id
                 WHERE o.space_id = ? AND o.status = 'ACTIVE' AND p.left_at IS NULL
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }

    /** 이탈 여부와 무관한 행 수. 재합류가 새 행을 만들지 않는지 본다. */
    private int participantRows(Long spaceId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.occupancy_participants p
                  JOIN learning_service.room_occupancies o ON o.id = p.occupancy_id
                 WHERE o.space_id = ? AND p.user_id = ?
                """, Integer.class, spaceId, userId);
        return count == null ? 0 : count;
    }

    @FunctionalInterface
    private interface Action {
        void run(UUID userId);
    }

    private record Result(int success, List<Throwable> errors) {
        int failure() {
            return errors.size();
        }
    }
}
