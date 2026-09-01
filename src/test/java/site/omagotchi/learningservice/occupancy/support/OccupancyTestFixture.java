package site.omagotchi.learningservice.occupancy.support;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.domain.PresenceInterval;
import site.omagotchi.learningservice.attendance.domain.PresenceState;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.attendance.infrastructure.PresenceIntervalRepository;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

/**
 * 점유 통합 테스트용 기수·멤버십·공간 픽스처.
 *
 * <p>프로덕션 코드는 파사드나 공개 계약을 통해서만 다른 파트에 접근하지만, 테스트
 * 픽스처는 리포지토리를 직접 써도 된다 — 의존 방향 규칙은 {@code src/main}에만 적용된다
 * ({@code TeamTestFixture}와 같은 규약).</p>
 *
 * <p>테스트마다 새 기수·새 공간을 만드는 것을 권장한다. 통합 테스트는 트랜잭션 롤백 없이
 * 같은 컨테이너를 공유하므로, 공간을 재사용하면 앞선 테스트가 남긴 활성 점유가
 * {@code uq_room_occupancies_one_active_per_space}에 걸려 엉뚱한 곳에서 실패한다.</p>
 */
@TestComponent
@RequiredArgsConstructor
public class OccupancyTestFixture {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CohortRepository cohortRepository;
    private final CohortMembershipRepository membershipRepository;
    private final SpaceRepository spaceRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final PresenceIntervalRepository presenceIntervalRepository;
    private final Clock clock;

    public Long createCohort(String name) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SEOUL);
        Cohort cohort = Cohort.create(
                name,
                "점유 테스트 기수",
                today,
                today.plusMonths(6),
                UUID.randomUUID()
        );
        return cohortRepository.save(cohort).getId();
    }

    /**
     * ACTIVE 멤버십을 만들고 <b>출근 처리까지 한다</b>.
     *
     * <p>점유·참여자 추가가 재실을 전제로 하므로(MR-22, MR-19), 멤버십만 만들면 전부
     * 403 {@code NOT_PRESENT}로 막힌다. 출결 파트가 {@code checkIn}에서 하는 것과 같은
     * 모양으로 열린 재실 구간({@code ended_at IS NULL})을 하나 만들어 둔다.</p>
     */
    public Member createActiveMember(Long cohortId) {
        return createActiveMember(cohortId, UUID.randomUUID());
    }

    /** 재실 없이 멤버십만 만든다. "출석하지 않은 사용자"를 재현할 때 쓴다. */
    public Member createAbsentMember(Long cohortId) {
        CohortMembership membership = CohortMembership.activeManager(
                cohortId, UUID.randomUUID(), UUID.randomUUID()
        );
        return new Member(membershipRepository.save(membership).getId(),
                membership.getUserId());
    }

    /**
     * 이 멤버십으로 출근시킨다 — 열린 재실 구간을 만든다.
     *
     * <p>{@code AttendanceService.checkIn}을 부르지 않는 이유는 그쪽이 기수 출결 정책
     * ({@code cohort_attendance_policies})을 요구하기 때문이다. 점유 테스트에 필요한 것은
     * "열린 구간이 있다"는 사실뿐이라 정책까지 세팅하지 않는다.</p>
     */
    public void checkIn(Long membershipId) {
        CohortMembership membership = membershipRepository.findById(membershipId).orElseThrow();
        Long labSpaceId = createLab(
                membership.getCohortId(),
                "점유-체크인-실습실-" + UUID.randomUUID(),
                100
        );
        Instant now = clock.instant();
        AttendanceRecord record = AttendanceRecord.start(
                membershipId,
                AggregationDateTime.aggregationDate(now)
        );
        record.checkIn(now, AttendanceStatus.PRESENT, 0);
        Long attendanceId = attendanceRecordRepository.save(record).getId();

        presenceIntervalRepository.save(PresenceInterval.start(
                attendanceId, PresenceState.PRESENT, labSpaceId, now));
    }

    /**
     * 퇴근시킨다 — 열린 재실 구간을 닫는다. "재실이 아닌 상태"를 만들 때 쓴다.
     *
     * <p>{@code end()} 뒤에 {@code save}가 필요하다. 이 픽스처는 {@code @Transactional}이
     * 아니라 조회한 엔티티가 곧바로 준영속이 되고, 그러면 변경 감지가 동작하지 않는다.</p>
     */
    public void checkOut(Long membershipId) {
        Instant now = clock.instant();
        attendanceRecordRepository
                .findByCohortMembershipIdAndAttendanceDate(
                        membershipId,
                        AggregationDateTime.aggregationDate(now)
                )
                .flatMap(record -> presenceIntervalRepository
                        .findByAttendanceIdAndEndedAtIsNullOrderByStartedAtAscIdAsc(record.getId())
                        .stream()
                        .findFirst())
                .ifPresent(interval -> {
                    interval.end(now);
                    presenceIntervalRepository.save(interval);
                });
    }

    /**
     * 같은 계정에 여러 기수의 멤버십을 붙일 때 쓴다.
     *
     * <p>다기수 담당 매니저가 회의실을 둘 잡을 수 없다는 것(MR-10)을 검증하는 시나리오의
     * 전제다. 역할이 MANAGER로 고정되지만 점유 로직은 역할을 읽지 않는다.</p>
     */
    public Member createActiveMember(Long cohortId, UUID userId) {
        CohortMembership membership = CohortMembership.activeManager(
                cohortId, userId, UUID.randomUUID()
        );
        Long membershipId = membershipRepository.save(membership).getId();
        checkIn(membershipId);
        return new Member(membershipId, userId);
    }

    /**
     * STUDENT 역할의 ACTIVE 멤버십을 만들고 출근시킨다.
     *
     * <p>{@link #createActiveMember}가 MANAGER로 고정돼 있어 "매니저가 아닌 사람"을
     * 재현할 수 없다. 강제 종료(MR-21)처럼 역할로 갈리는 권한을 검증하려면 둘이 구분돼야
     * 한다 — 이 Method가 없으면 권한 테스트가 항상 통과해 아무것도 지키지 못한다.</p>
     */
    public Member createActiveStudent(Long cohortId) {
        UUID userId = UUID.randomUUID();

        // CohortMembership에 "ACTIVE 학생"을 만드는 공개 경로가 없다 — 승인 흐름은
        // CohortMembershipService를 거치며 기수 정책까지 요구한다. 점유 테스트에 필요한
        // 것은 역할뿐이라 ACTIVE 매니저를 만든 뒤 역할만 바꾼다.
        CohortMembership membership = CohortMembership.activeManager(
                cohortId, userId, UUID.randomUUID());
        ReflectionTestUtils.setField(membership, "role", CohortMembershipRole.STUDENT);

        Long membershipId = membershipRepository.save(membership).getId();
        checkIn(membershipId);
        return new Member(membershipId, userId);
    }

    /**
     * 활성 회의실을 만든다.
     *
     * <p>{@code Space.create}는 비활성으로 만들므로 반드시 {@code activate}를 거친다 —
     * 비활성 공간은 점유가 400으로 거부된다(RM-13).</p>
     */
    public Long createMeetingRoom(Long cohortId, String name, int capacity) {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), SEOUL);
        Space space = Space.create(name, SpaceType.MEETING, capacity, cohortId, now).activate(now);
        return spaceRepository.save(space).getId();
    }

    /** 회의실이 아닌 공간. 유형 검증(MR-20)에 쓴다. */
    public Long createLab(Long cohortId, String name, int capacity) {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), SEOUL);
        Space space = Space.create(name, SpaceType.LAB, capacity, cohortId, now).activate(now);
        return spaceRepository.save(space).getId();
    }

    /** 독서실. 점유 대상이 아니며 관리 주체 순환(CE-04) 검증에 쓴다. */
    public Long createStudyRoom(Long cohortId, String name, int capacity) {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), SEOUL);
        Space space = Space.create(name, SpaceType.STUDY, capacity, cohortId, now).activate(now);
        return spaceRepository.save(space).getId();
    }

    /**
     * 만들어진 멤버십의 두 얼굴.
     *
     * <p>둘 다 필요한 이유는 계층마다 쓰는 키가 다르기 때문이다. API 진입은 계정
     * id({@code userId})로 하지만, 점유 행의 배타 유니크가 걸리는 것도 계정이고
     * 기수 도출에 쓰이는 것은 {@code membershipId}다.</p>
     */
    public record Member(Long membershipId, UUID userId) {
    }
}
