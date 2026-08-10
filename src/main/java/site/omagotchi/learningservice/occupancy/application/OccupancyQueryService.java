package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.ActiveSpaceOccupancy;
import site.omagotchi.learningservice.occupancy.application.result.SpaceOccupancyView;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 다른 Feature가 점유 상태를 조회하는 공개 계약.
 *
 * <p>{@code space} 파트가 공간 목록의 사용 상태를 파생 계산하는 것이 첫 소비처다.
 * 공간은 "사용 중"을 컬럼으로 저장하지 않고 활성 점유의 존재로 매 조회 시 판단한다
 * (명세서 01, SSOT 원칙) — 그 판단에 필요한 사실만 여기서 제공한다.</p>
 *
 * <p><b>"사용 중"의 정의를 이 Feature가 단독으로 소유한다.</b> {@code status = ACTIVE}이고
 * {@code expires_at > now}인 행 하나가 그 정의이며, 두 메서드가 같은 기준을 쓴다.
 * 소비처가 점유 테이블을 직접 읽으면 이 정의가 복제되고, 만료 스케줄러(#9)나 강제
 * 종료(MR-21)로 조건이 바뀔 때 한쪽만 고쳐져 같은 방이 목록에서는 공실인데 점유하면
 * 409인 상태가 된다.</p>
 *
 * <p>노출 정책은 여기서 판정하지 않는다. 점유자·참여자를 같은 기수에만 보여주는 것은
 * (MR-36) 화면 정책이라 소비처가 소유하며, 이 Service는 그 판정 근거인
 * {@code occupierCohortId}까지만 제공한다 — 자세한 계약은 {@link SpaceOccupancyView} 참고.</p>
 *
 * <p>연장·반납({@link RoomOccupancyLifecycleService})과 나눈 이유는 방향이 다르기 때문이다.
 * 저쪽은 점유의 상태를 바꾸고, 여기는 읽기만 한다 — 트랜잭션 성격도 의존성도 겹치지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OccupancyQueryService {

    private final RoomOccupancyRepository occupancyRepository;
    private final OccupancyParticipantRepository participantRepository;
    private final CohortMembershipQueryService cohortMembershipQueryService;

    /**
     * 이 공간이 지금 사용 중인가.
     *
     * <p>공간 수정·비활성화·삭제 전 가드가 소비처다 — 사용 중인 회의실의 설정을 바꾸면
     * 그 안에서 회의하는 사람들이 영향을 받는다.</p>
     *
     * <p>{@link #findActiveBySpaceIds}와 판정 기준이 같다. 목록에 "사용 중"으로 뜨는 방은
     * 여기서도 반드시 {@code true}다.</p>
     *
     * @param now 판정 기준 시각. 만료된 점유는 사용 중이 아니다
     * @throws IllegalArgumentException 인자가 {@code null}이면. 호출 계약 위반이므로
     *                                  기본값으로 넘기지 않는다
     */
    public boolean existsActive(Long spaceId, OffsetDateTime now) {
        requireNotNull(spaceId, "spaceId");
        requireNotNull(now, "now");
        return occupancyRepository.existsActiveBySpaceId(spaceId, now);
    }

    /**
     * 여러 회의실의 현재 점유 상태를 한 번에 조회한다.
     *
     * <p><b>배치인 것이 계약의 일부다.</b> 공간이 N개여도 쿼리는 3회로 고정된다 —
     * 점유·참여자·기수 각 1회. 목록을 돌며 단건 조회를 부르면 기수·공간 모듈이 분리될 때
     * 그대로 N+1 원격 호출이 된다.</p>
     *
     * <p>{@code now}로 만료된 점유를 제외한다. 유니크 인덱스는 {@code status}만 보고
     * {@code expires_at}은 보지 않아서, 스케줄러(#9)가 아직 쓸어가지 않은 행이 ACTIVE로
     * 남아 있다. 이 필터가 없으면 <b>목록에는 "사용 중"인데 점유는 성공하는</b> 상태가
     * 사용자에게 보인다.</p>
     *
     * <p>결과를 {@code spaceId}로 키잡아 돌려주는 것은 소비처가 공간 목록과 조인하기
     * 때문이다. 공간당 최대 1건임은 {@code uq_room_occupancies_one_active_per_space}가
     * 보장하므로 중복 키를 걱정할 필요가 없다.</p>
     *
     * @param spaceIds 조회할 회의실. 비어 있으면 빈 결과
     * @param now      판정 기준 시각. 목록 전체가 같은 시각을 봐야 하므로 호출부가 고정해 넘긴다
     * @return 사용 중인 회의실만 담긴 {@code spaceId → 점유 상태}. 비어 있는 방은 키가 없다
     * @throws IllegalArgumentException 인자가 {@code null}이면
     */
    public Map<Long, SpaceOccupancyView> findActiveBySpaceIds(
            Collection<Long> spaceIds, OffsetDateTime now) {

        requireNotNull(spaceIds, "spaceIds");
        requireNotNull(now, "now");
        if (spaceIds.isEmpty()) {
            return Map.of();
        }

        List<ActiveSpaceOccupancy> occupancies =
                occupancyRepository.findActiveBySpaceIds(spaceIds, now);
        if (occupancies.isEmpty()) {
            return Map.of();
        }

        // 기수는 점유 행에 없다 (ERD v3). occupier_membership_id로 기수 파트에 되묻는데,
        // 이 조회를 여기 두는 것이 요점이다 — Port가 cohort_memberships를 조인하면
        // 점유의 infrastructure가 남의 테이블을 알게 된다.
        Map<Long, Long> cohortIdsByMembershipId = cohortMembershipQueryService.findCohortIds(
                occupancies.stream().map(ActiveSpaceOccupancy::occupierMembershipId).toList());

        Map<Long, List<UUID>> participantsByOccupancyId =
                participantRepository.findActiveUserIdsByOccupancyIds(
                        occupancies.stream().map(ActiveSpaceOccupancy::occupancyId).toList());

        // toMap이 아니라 LinkedHashMap인 것은 조회 순서를 유지하기 위해서다.
        Map<Long, SpaceOccupancyView> viewsBySpaceId = new LinkedHashMap<>();
        for (ActiveSpaceOccupancy occupancy : occupancies) {
            viewsBySpaceId.put(occupancy.spaceId(), SpaceOccupancyView.of(
                    occupancy,
                    cohortIdsByMembershipId.get(occupancy.occupierMembershipId()),
                    participantsByOccupancyId.getOrDefault(occupancy.occupancyId(), List.of())
            ));
        }
        return viewsBySpaceId;
    }

    /**
     * 호출 계약 위반을 조용히 통과시키지 않는다 (04-error-handling §2).
     *
     * <p>{@code now}가 빠졌을 때 빈 결과를 돌려주면 소비처는 <b>모든 회의실이 공실</b>이라는
     * 그럴듯한 응답을 받는다. 사용 중인 방이 점유 가능해 보이고, 공간 비활성화 가드도
     * 통과한다 — 조회가 실패했다는 사실이 어디에도 남지 않는 것이 문제다.</p>
     */
    private static void requireNotNull(Object argument, String name) {
        if (argument == null) {
            throw new IllegalArgumentException(name + "은(는) null일 수 없습니다.");
        }
    }
}
