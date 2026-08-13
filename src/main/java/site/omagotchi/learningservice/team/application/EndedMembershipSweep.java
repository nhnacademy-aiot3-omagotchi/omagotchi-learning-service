package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository.MembershipRef;

import java.util.List;
import java.util.Set;

/**
 * 종료된 소속의 팀 정리가 유실됐을 때 되찾는 정합성 스윕 (ADR space-team/0013).
 *
 * <p><b>불변식은 하나다</b> — {@code team_members}의 모든 행은 ACTIVE 소속을 가리킨다.
 * 어긋난 행을 찾아 {@link TeamMasterService#removeEndedMember}를 다시 호출한다.</p>
 *
 * <p><b>이벤트를 대체하지 않고 뒤를 받친다.</b> 정상 경로는
 * {@code TeamMembershipEndedListener}가 커밋 직후 처리하고, 여기는 그것이 실패했을 때만
 * 늦게 개입한다. 인메모리 이벤트는 전달 상태를 보존하지 않아
 * {@code CohortMembershipService#end}가 재발행하지 못하는데(두 번째 호출은 이미 ENDED라
 * 0행), 그 틈을 메우는 것이 이 Class의 존재 이유다.</p>
 *
 * <p><b>{@link TeamMasterService} 밖에 있는 것이 핵심이다.</b> 같은 Class 안에서
 * {@code removeEndedMember}를 부르면 Spring Proxy를 거치지 않아 {@code @Transactional}이
 * 적용되지 않는다 — 건별 경계가 사라져 한 건의 실패가 배치 전체를 되돌린다
 * ({@code OccupancyExpiration}과 같은 이유).</p>
 *
 * <p><b>기수 파트 테이블을 직접 읽지 않는다.</b> 소속이 살아 있는지는
 * {@link CohortMembershipQueryService#findInactiveMembershipIds}에 묻는다. 팀이
 * {@code cohort_memberships}를 조인하면 남의 테이블을 알게 된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndedMembershipSweep {

    /**
     * 한 실행에서 처리할 최대 배치 수.
     *
     * <p>순회가 끝나지 않는 상황(커서가 전진하지 못하는 버그 등)에서 스케줄러 스레드를
     * 영원히 붙들지 않기 위한 상한이다. 정상 상태에서는 도달하지 않는다 —
     * {@code team_members}는 물리 삭제라 현재 소속 수로 유계이기 때문이다.</p>
     */
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final TeamMemberRepository teamMemberRepository;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final TeamMasterService teamMasterService;

    /**
     * 고아 소속 행을 찾아 정리한다.
     *
     * <p><b>이 Method에 트랜잭션이 없는 것이 의도다.</b> 정리는 건별로
     * {@link TeamMasterService#removeEndedMember}가 자기 트랜잭션에서 수행한다. 여기에
     * 트랜잭션을 두면 조회 한 번을 위해 커넥션을 전체 순회가 끝날 때까지 붙들고,
     * 건별 격리도 무의미해진다.</p>
     *
     * <p>커서로 전진하는 이유는 조회 대상이 "고아"가 아니라 <b>전체 소속 행</b>이기
     * 때문이다. 고아 여부는 기수 파트에 물어봐야 알 수 있어 SQL로 걸러낼 수 없으므로,
     * {@code LIMIT}만 두면 앞쪽 배치만 반복해서 보고 뒤쪽에 영원히 닿지 못한다.</p>
     *
     * <p>건별 실패는 잡아서 다음 대상으로 넘어간다. 실패한 행은 그대로 남아 다음 주기에
     * 다시 조회되므로 별도의 실패 상태 기록이 필요 없다.</p>
     *
     * @param batchSize 한 배치에서 훑을 소속 행 수
     * @return 이번 실행으로 실제 정리된 건수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int sweep(int batchSize) {
        long cursor = 0L;
        int cleaned = 0;
        int detected = 0;

        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<MembershipRef> refs =
                    teamMemberRepository.findMembershipRefsAfter(cursor, batchSize);
            if (refs.isEmpty()) {
                break;
            }

            // 소속 유효성은 기수 파트가 판정한다. 배치당 1회 호출이며,
            // 조회가 실패하거나 비면 정리 대상이 0건이 되어 안전 측으로 기운다.
            Set<Long> inactiveMembershipIds = cohortMembershipQueryService
                    .findInactiveMembershipIds(refs.stream()
                            .map(MembershipRef::cohortMembershipId)
                            .toList());

            for (MembershipRef ref : refs) {
                if (!inactiveMembershipIds.contains(ref.cohortMembershipId())) {
                    continue;
                }
                detected++;
                cleaned += cleanUp(ref.cohortMembershipId()) ? 1 : 0;
            }

            cursor = refs.getLast().teamMemberId();
            if (refs.size() < batchSize) {
                break;
            }
        }

        report(detected, cleaned);
        return cleaned;
    }

    /**
     * 한 건을 정리한다. 실패해도 나머지를 막지 않는다.
     *
     * <p>{@code false}가 정상적으로 나올 수 있다 — 조회와 정리 사이에 이벤트 리스너가
     * 먼저 처리했으면 소속 행이 이미 없다. 그때 예외를 던지지 않고 넘어가는 것이
     * {@code removeEndedMember}의 멱등 계약이다.</p>
     */
    private boolean cleanUp(Long cohortMembershipId) {
        try {
            return teamMasterService.removeEndedMember(cohortMembershipId);
        } catch (Exception exception) {
            // 여기서 다시 던지면 남은 대상이 이번 주기에 처리되지 않는다.
            // 행은 그대로 남아 다음 주기가 다시 집어 간다.
            log.error("고아 팀원 정리에 실패했습니다. cohortMembershipId={}",
                    cohortMembershipId, exception);
            return false;
        }
    }

    /**
     * 검출 결과를 남긴다.
     *
     * <p><b>정상 기대값은 0건이다.</b> 0이 아닌 값이 지속되면 "스윕이 잘 고치고 있다"가
     * 아니라 <b>이벤트 경로가 고장 났다는 신호</b>로 읽어야 한다 — 스윕이 증상을 가려
     * 원인을 묻히게 두지 않으려고 검출 건수를 정리 건수와 따로 남긴다.</p>
     */
    private void report(int detected, int cleaned) {
        if (detected == 0) {
            log.debug("정합성 스윕: 고아 팀원이 없습니다.");
            return;
        }
        log.warn("정합성 스윕: 고아 팀원 {}건을 검출해 {}건을 정리했습니다. "
                + "이벤트 경로가 정상이면 검출은 0건이어야 합니다.", detected, cleaned);
    }
}
