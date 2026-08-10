package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.OccupancyErrorCode;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantService;
import site.omagotchi.learningservice.occupancy.application.OccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

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
    RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    @Autowired
    OccupancyQueryService occupancyQueryService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    OccupancyParticipantRepository participantRepository;

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

    // ────────────────────────────── 반납 (MR-14, MR-32) ──────────────────────────────

    /**
     * 부분 유니크가 {@code status='ACTIVE'}에만 걸린다는 것을 실제 DB로 확인한다.
     * 종료 행이 인덱스에서 빠지지 않으면 반납한 방을 아무도 다시 잡을 수 없다.
     */
    @Test
    @DisplayName("반납한 회의실은 다른 사람이 즉시 다시 점유할 수 있다.")
    void test8() {
        Long cohortId = fixture.createCohort("반납 재점유 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "반납 재점유 회의실", 8);

        UUID firstUserId = fixture.createActiveMember(cohortId).userId();
        UUID secondUserId = fixture.createActiveMember(cohortId).userId();

        roomOccupancyService.start(spaceId, firstUserId);
        roomOccupancyLifecycleService.release(spaceId, firstUserId);

        roomOccupancyService.start(spaceId, secondUserId);

        assertThat(activeOccupancies(spaceId)).isEqualTo(1);
    }

    /**
     * 참여자는 물리 삭제가 아니라 {@code left_at} 기록이다 (MR-32). 행이 사라지면 참여
     * 이력이 없어지고, 반대로 {@code left_at}이 비면 그 사람이 영구히 다른 회의에
     * 참여할 수 없게 된다 — 행 수와 열린 행 수를 함께 본다.
     */
    @Test
    @DisplayName("반납하면 참여자 행이 삭제되지 않고 left_at만 채워진다.")
    void test9() {
        Long cohortId = fixture.createCohort("반납 마감 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "반납 마감 회의실", 8);

        UUID occupierUserId = fixture.createActiveMember(cohortId).userId();
        UUID participantUserId = fixture.createActiveMember(cohortId).userId();

        roomOccupancyService.start(spaceId, occupierUserId);
        occupancyParticipantService.add(spaceId, participantUserId, occupierUserId);
        assertThat(activeParticipants(spaceId)).isEqualTo(2);

        roomOccupancyLifecycleService.release(spaceId, occupierUserId);

        // 점유자 본인의 행도 함께 닫힌다 — 시작이 점유자를 참여자로 등록했으므로(MR-27).
        assertThat(allParticipantRows(spaceId)).isEqualTo(2);
        assertThat(openParticipantRows(spaceId)).isZero();
    }

    /** 반납한 사람은 참여 이력이 닫혔으므로 다른 회의실을 곧바로 점유할 수 있어야 한다. */
    @Test
    @DisplayName("반납 후에는 같은 사람이 다른 회의실을 점유할 수 있다.")
    void test10() {
        Long cohortId = fixture.createCohort("반납 후 재점유 기수");
        Long firstRoomId = fixture.createMeetingRoom(cohortId, "반납 후 회의실 A", 8);
        Long secondRoomId = fixture.createMeetingRoom(cohortId, "반납 후 회의실 B", 8);

        UUID userId = fixture.createActiveMember(cohortId).userId();

        roomOccupancyService.start(firstRoomId, userId);
        roomOccupancyLifecycleService.release(firstRoomId, userId);

        roomOccupancyService.start(secondRoomId, userId);

        assertThat(activeOccupancies(firstRoomId)).isZero();
        assertThat(activeOccupancies(secondRoomId)).isEqualTo(1);
    }

    /**
     * 점유 행 락이 반납끼리도 직렬화하는지 본다. 둘 다 성공하면 종료 사유나 시각이
     * 덮어써지고, 참여자 마감이 두 번 일어난다.
     */
    @Test
    @DisplayName("같은 점유에 반납이 동시에 와도 한 건만 성공한다.")
    void test11() throws Exception {
        Long cohortId = fixture.createCohort("동시 반납 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "동시 반납 회의실", 8);

        UUID occupierUserId = fixture.createActiveMember(cohortId).userId();
        roomOccupancyService.start(spaceId, occupierUserId);

        // 같은 점유자가 반납 버튼을 연타한 상황이다.
        List<UUID> requesters = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            requesters.add(occupierUserId);
        }

        Result result = runConcurrently(requesters,
                userId -> roomOccupancyLifecycleService.release(spaceId, userId));

        assertThat(result.success()).isEqualTo(1);
        assertThat(activeOccupancies(spaceId)).isZero();
        assertThat(result.errors()).allSatisfy(thrown -> assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(OccupancyErrorCode.OCCUPANCY_ENDED));
    }

    /**
     * {@code release()}가 상태 변경(도메인 필드 대입)과 참여자 마감(별도 저장소 호출)을
     * 같은 트랜잭션에서 처리하는지 실제 커밋으로 확인한다.
     *
     * <p>도메인 쪽({@code occupancy.release(now)})은 필드 대입일 뿐이라 자체적으로 실패할
     * 방법이 없다 — 그래서 두 번째 저장소 호출({@code closeAllActiveByOccupancyId})에만
     * 실패를 주입한다. 이 클래스는 트랜잭션을 롤백하지 않고 같은 컨테이너를 공유하므로,
     * 예외 이후 실제로 커밋된 DB 상태를 그대로 조회해 원자성을 검증할 수 있다
     * ({@code TimerCommandServiceIT.rollsBackTimerStopWhenStudyRecordSaveFails}와 같은 패턴).</p>
     */
    @Test
    @DisplayName("참여자 마감이 실패하면 점유 상태 변경도 함께 롤백된다.")
    void test18() {
        Long cohortId = fixture.createCohort("반납 롤백 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "반납 롤백 회의실", 8);

        UUID occupierUserId = fixture.createActiveMember(cohortId).userId();
        UUID participantUserId = fixture.createActiveMember(cohortId).userId();

        roomOccupancyService.start(spaceId, occupierUserId);
        occupancyParticipantService.add(spaceId, participantUserId, occupierUserId);

        doThrow(new RuntimeException("참여자 마감 실패"))
                .when(participantRepository).closeAllActiveByOccupancyId(anyLong(), any());

        Throwable thrown = catchThrowable(
                () -> roomOccupancyLifecycleService.release(spaceId, occupierUserId));

        assertThat(thrown).isInstanceOf(RuntimeException.class);

        // 원자적 롤백 검증 — status·ended_at·참여자 두 명의 left_at이 모두 시도 전 상태여야 한다.
        assertThat(activeOccupancies(spaceId)).isEqualTo(1);
        assertThat(occupancyStatus(spaceId)).isEqualTo("ACTIVE");
        assertThat(occupancyEndedAt(spaceId)).isNull();
        assertThat(openParticipantRows(spaceId)).isEqualTo(2);
    }

    // ────────────────────────────── 재실 검증 (MR-22, MR-19) ──────────────────────────────

    /**
     * 스텁이 아니라 실제 {@code presence_intervals}를 읽는지 확인한다. 멤버십이 ACTIVE라도
     * 열린 재실 구간이 없으면 막혀야 한다 — 스텁으로 되돌아가면 이 테스트가 먼저 깨진다.
     */
    @Test
    @DisplayName("출근하지 않은 사용자는 회의실을 점유할 수 없다.")
    void test12() {
        Long cohortId = fixture.createCohort("비재실 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "비재실 회의실", 8);
        UUID absentUserId = fixture.createAbsentMember(cohortId).userId();

        Throwable thrown = catchThrowable(() -> roomOccupancyService.start(spaceId, absentUserId));

        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OccupancyErrorCode.NOT_PRESENT);
        assertThat(activeOccupancies(spaceId)).isZero();
    }

    @Test
    @DisplayName("출근하지 않은 사용자는 참여자로 추가할 수 없다.")
    void test13() {
        Long cohortId = fixture.createCohort("비재실 참여자 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "비재실 참여자 회의실", 8);

        UUID occupierUserId = fixture.createActiveMember(cohortId).userId();
        UUID absentUserId = fixture.createAbsentMember(cohortId).userId();
        roomOccupancyService.start(spaceId, occupierUserId);

        Throwable thrown = catchThrowable(
                () -> occupancyParticipantService.add(spaceId, absentUserId, occupierUserId));

        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OccupancyErrorCode.TARGET_NOT_PRESENT);
        assertThat(activeParticipants(spaceId)).isEqualTo(1);
    }

    /** 퇴근하면 구간이 닫히므로 더 이상 재실이 아니다 — 열린 구간만 재실로 본다. */
    @Test
    @DisplayName("퇴근해 재실 구간이 닫히면 점유할 수 없다.")
    void test14() {
        Long cohortId = fixture.createCohort("퇴근 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "퇴근 회의실", 8);
        var member = fixture.createActiveMember(cohortId);

        fixture.checkOut(member.membershipId());

        Throwable thrown = catchThrowable(() -> roomOccupancyService.start(spaceId, member.userId()));

        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OccupancyErrorCode.NOT_PRESENT);
    }

    /**
     * 점유자 멤버십의 출처가 실제 재실 구간인지 확인한다 (명세서 §6 마지막 항목).
     * 다기수 담당자는 기수마다 출결 기록이 따로 생겨 열린 구간이 둘일 수 있고,
     * 그때 최신 구간의 멤버십이 점유자 멤버십이 된다.
     */
    @Test
    @DisplayName("다기수 담당자가 점유하면 최신 재실 구간의 멤버십이 기록된다.")
    void test15() {
        Long firstCohortId = fixture.createCohort("재실 도출 3기");
        Long secondCohortId = fixture.createCohort("재실 도출 4기");
        Long spaceId = fixture.createMeetingRoom(firstCohortId, "재실 도출 회의실", 8);

        UUID managerUserId = UUID.randomUUID();
        fixture.createActiveMember(firstCohortId, managerUserId);            // 먼저 출근
        var later = fixture.createActiveMember(secondCohortId, managerUserId); // 나중에 출근

        roomOccupancyService.start(spaceId, managerUserId);

        assertThat(occupierMembershipId(spaceId)).isEqualTo(later.membershipId());
    }

    // ────────────────── 공간 파트에 제공하는 조회 계약 ──────────────────

    /**
     * JPQL 생성자 표현식은 컴파일로 검증되지 않는다 — 실제 DB에서 한 번은 돌려봐야
     * {@code SpaceOccupancyView}의 시그니처가 맞는지 확인된다.
     */
    @Test
    @DisplayName("여러 회의실의 점유 상태를 한 번에 조회한다.")
    void test16() {
        Long cohortId = fixture.createCohort("배치 조회 기수");
        Long occupiedRoomId = fixture.createMeetingRoom(cohortId, "배치 조회 사용중", 8);
        Long emptyRoomId = fixture.createMeetingRoom(cohortId, "배치 조회 빈방", 8);

        UUID occupierUserId = fixture.createActiveMember(cohortId).userId();
        roomOccupancyService.start(occupiedRoomId, occupierUserId);

        Map<Long, SpaceOccupancyView> found = occupancyQueryService.findActiveBySpaceIds(
                List.of(occupiedRoomId, emptyRoomId), OffsetDateTime.now());

        // 빈 방은 키가 없다 — 소비처가 null 여부로 사용 상태를 판단한다.
        assertThat(found).containsOnlyKeys(occupiedRoomId);
        assertThat(found.get(occupiedRoomId).expiresAt()).isNotNull();
    }

    /**
     * 유니크 인덱스는 {@code status}만 보고 {@code expires_at}은 보지 않는다. 이 필터가
     * 없으면 목록에는 "사용 중"으로 뜨는데 점유는 성공하는 상태가 사용자에게 보인다.
     */
    @Test
    @DisplayName("만료된 점유는 사용 중으로 세지 않는다.")
    void test17() {
        Long cohortId = fixture.createCohort("만료 제외 기수");
        Long spaceId = fixture.createMeetingRoom(cohortId, "만료 제외 회의실", 8);

        UUID occupierUserId = fixture.createActiveMember(cohortId).userId();
        roomOccupancyService.start(spaceId, occupierUserId);

        // 스케줄러(#9)가 아직 쓸어가지 않아 status는 ACTIVE인 채로 만료된 상태를 만든다.
        // started_at도 함께 당긴다 — ck_room_occupancies_period가 expires_at > started_at을 요구한다.
        jdbcTemplate.update("""
                UPDATE learning_service.room_occupancies
                   SET started_at = now() - interval '3 hours',
                       expires_at = now() - interval '1 minute'
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, spaceId);

        assertThat(occupancyQueryService.findActiveBySpaceIds(
                List.of(spaceId), OffsetDateTime.now())).isEmpty();
        assertThat(activeOccupancies(spaceId)).isEqualTo(1);   // 행 자체는 그대로다
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private String occupancyStatus(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, String.class, spaceId);
    }

    private OffsetDateTime occupancyEndedAt(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT ended_at FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, OffsetDateTime.class, spaceId);
    }

    private Long occupierMembershipId(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT occupier_membership_id FROM learning_service.room_occupancies
                 WHERE space_id = ? AND status = 'ACTIVE'
                """, Long.class, spaceId);
    }

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

    /** 이탈 여부와 무관한 이 점유의 전체 참여자 행 수. 반납이 행을 지우지 않는지 본다. */
    private int allParticipantRows(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.occupancy_participants p
                  JOIN learning_service.room_occupancies o ON o.id = p.occupancy_id
                 WHERE o.space_id = ?
                """, Integer.class, spaceId);
        return count == null ? 0 : count;
    }

    /** 아직 열려 있는 참여자 행 수. 점유 상태와 무관하게 센다. */
    private int openParticipantRows(Long spaceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM learning_service.occupancy_participants p
                  JOIN learning_service.room_occupancies o ON o.id = p.occupancy_id
                 WHERE o.space_id = ? AND p.left_at IS NULL
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
