package site.omagotchi.learningservice.occupancy.support;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

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

    public Long createCohort(String name) {
        Cohort cohort = Cohort.create(
                name,
                "점유 테스트 기수",
                LocalDate.now(),
                LocalDate.now().plusMonths(6),
                UUID.randomUUID()
        );
        return cohortRepository.save(cohort).getId();
    }

    public Member createActiveMember(Long cohortId) {
        return createActiveMember(cohortId, UUID.randomUUID());
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
        return new Member(membershipRepository.save(membership).getId(), userId);
    }

    /**
     * 활성 회의실을 만든다.
     *
     * <p>{@code Space.create}는 비활성으로 만들므로 반드시 {@code activate}를 거친다 —
     * 비활성 공간은 점유가 400으로 거부된다(RM-13).</p>
     */
    public Long createMeetingRoom(Long cohortId, String name, int capacity) {
        ZonedDateTime now = ZonedDateTime.now(SEOUL);
        Space space = Space.create(name, SpaceType.MEETING, capacity, cohortId, now).activate(now);
        return spaceRepository.save(space).getId();
    }

    /** 회의실이 아닌 공간. 유형 검증(MR-20)에 쓴다. */
    public Long createLab(Long cohortId, String name, int capacity) {
        ZonedDateTime now = ZonedDateTime.now(SEOUL);
        Space space = Space.create(name, SpaceType.LAB, capacity, cohortId, now).activate(now);
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
