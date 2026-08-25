package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.team.application.port.TeamRepository;

import java.util.List;

/**
 * 기수 종료 시 그 기수의 활성 팀을 전부 해체한다 (CE-01, 명세 08 §2 1단계).
 *
 * <p>{@code CohortEndedSpaceCleanup}(CE-04)과 같은 자리다 — 정리 대상을 소유한 Feature가
 * 자기 정리를 맡고, 훅 오케스트레이터({@code CohortEndedCleanup})는 Method 하나만 부른다.
 * 그래야 훅이 남의 Feature Port를 직접 알지 않는다.</p>
 *
 * <p><b>루프가 {@link TeamMasterService} 밖에 있는 것이 핵심이다.</b> 같은 Class 안에서
 * {@code disbandOne}을 부르면 자기호출이라 Spring Proxy를 거치지 않아 팀별
 * {@code @Transactional}이 성립하지 않는다 — 한 팀의 실패가 앞서 처리된 팀까지 되돌린다.
 * {@code EndedMembershipSweep}이 루프를 밖에 둔 것과 같은 이유다 (ADR space-team/0013).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CohortEndedTeamCleanup {

    private final TeamRepository teamRepository;
    private final TeamMasterService teamMasterService;

    /**
     * 해체 결과를 건별로 격리한다 — 한 팀의 실패가 나머지 팀을 막지 않는다. 실패한 팀은
     * 소속이 이미 ENDED이므로 멤버십 정합성 스윕이 뒤늦게 받친다 (ADR space-team/0013).
     *
     * <p>이 Method에 Transaction이 없는 것이 의도다. Transaction 경계는 팀마다
     * {@link TeamMasterService#disbandOne}이 소유한다.</p>
     *
     * @return 이번 호출로 해체한 팀 수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int disbandAllByCohort(Long cohortId) {
        List<Long> teamIds = teamRepository.findActiveIdsByCohortId(cohortId);

        int disbanded = 0;
        for (Long teamId : teamIds) {
            try {
                if (teamMasterService.disbandOne(teamId)) {
                    disbanded++;
                }
            } catch (Exception exception) {
                log.error("기수 종료 팀 해체(CE-01)에 실패했습니다. teamId={}", teamId, exception);
            }
        }
        if (disbanded > 0) {
            log.info("기수 종료로 팀을 해체했습니다. cohortId={}, 해체={}팀", cohortId, disbanded);
        }
        return disbanded;
    }
}
