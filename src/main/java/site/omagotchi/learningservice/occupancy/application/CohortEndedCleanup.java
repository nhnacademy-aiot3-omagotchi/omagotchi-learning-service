package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.space.application.CohortEndedSpaceCleanup;
import site.omagotchi.learningservice.team.application.TeamMasterService;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 기수 종료 시 팀·알림·점유·실습실을 정해진 순서로 정리한다 (CE-01~04, 명세 08).
 *
 * <p><b>이 Class의 존재 이유는 CE-05의 순서다.</b> 멤버십 단위 이벤트로 팬아웃하면 비동기라
 * 순서를 보장할 수 없어, 기수 종료는 한 곳에서 단계를 밟는다 (명세 08 §1). 특히 대기 알림
 * 삭제(CE-02)가 점유 종료(CE-03)보다 <b>반드시 먼저</b>다 — 뒤집히면 CE-03의 공실 발송이
 * 방금 종료된 기수의 신청을 대기 중으로 보고 <b>그 학생들에게 알림을 보낸다.</b></p>
 *
 * <p><b>단계별 격리가 계약이다</b> (명세 08 §5). 각 단계가 자기 Transaction에서 커밋되고,
 * 한 단계의 실패가 성공한 단계를 되돌리지 않는다. 예외는 하나 — CE-02가 실패하면 CE-03을
 * <b>건너뛴다.</b> 격리라고 진행하면 위의 순서 역전이 그대로 일어나기 때문이다. 건너뛴
 * 점유는 만료 스케줄러(#9)가 최대 3시간 안에 정리하므로 잘못된 알림보다 지연이 낫다.</p>
 *
 * <p><b>진입점이 아직 없다</b> (명세 08 §7 미해결). 기수 파트가 이벤트를 발행할지 본 파트가
 * 배치로 폴링할지 계약되지 않아, 트리거가 정해지면 리스너 또는 스케줄러가 이 Method를
 * 부른다. {@code CohortMembershipEndedEvent}(CE-07)로 대체하지 않는 이유는 그 이벤트
 * javadoc에 있다 — CE-04는 기수 단위라 멤버십 이벤트로 표현되지 않는다.</p>
 *
 * <p>멤버십 목록은 기수 파트에 묻고({@code findMembershipIds}), <b>상태를 가리지 않는다</b>
 * — 종료 훅이 도는 시점에는 기수 파트가 멤버십을 이미 ENDED로 바꿨을 수 있다. 활성으로
 * 좁히면 대상을 하나도 찾지 못해 활성 점유와 대기 신청이 잔존한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CohortEndedCleanup {

    private final TeamMasterService teamMasterService;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final VacancyAlertService vacancyAlertService;
    private final RoomOccupancyRepository occupancyRepository;
    private final EndedMembershipOccupancyCleanup occupancyCleanup;
    private final CohortEndedSpaceCleanup spaceCleanup;
    private final Clock clock;

    /**
     * 종료된 기수를 4단계로 정리한다. 같은 기수에 두 번 호출해도 안전하다 — 각 단계가
     * 조건부 연산이라 두 번째는 대상이 없다 (명세 08 §5 "훅 중복 수신").
     *
     * <p><b>이 Method에 Transaction이 없는 것이 의도다.</b> 단계마다 자기 Transaction을
     * 열어야 부분 실패가 격리된다 — 여기에 하나로 두르면 마지막 단계의 실패가 팀 해체까지
     * 되돌리고, 기수 종료 자체를 막게 된다 (명세 08 §5 "기수 종료는 롤백하지 않음").</p>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void cleanUp(Long endedCohortId) {

        // 1단계 — 팀 정리 (CE-01). 실패해도 나머지 단계와 무관하다.
        try {
            teamMasterService.disbandAllByCohort(endedCohortId);
        } catch (Exception exception) {
            log.error("기수 종료 팀 정리(CE-01)에 실패했습니다. cohortId={}", endedCohortId, exception);
        }

        // CE-02·03의 대상은 멤버십 조인으로만 나온다 (명세 08 §1). 이 조회가 실패하면 둘 다
        // 대상을 특정할 수 없으므로 함께 건너뛰고, 기수 단위인 CE-04만 진행한다.
        List<Long> membershipIds;
        try {
            membershipIds = cohortMembershipQueryService.findMembershipIds(endedCohortId);
        } catch (Exception exception) {
            log.error("기수 종료 멤버십 조회에 실패해 알림 삭제(CE-02)·점유 종료(CE-03)를 건너뜁니다. cohortId={}",
                    endedCohortId, exception);
            membershipIds = null;
        }

        if (membershipIds != null) {
            // 2단계 — 대기 알림 삭제 (CE-02). 3단계보다 반드시 먼저다.
            boolean alertsCleared = discardAlerts(endedCohortId, membershipIds);

            // 3단계 — 활성 점유 종료 (CE-03). CE-02가 실패했으면 진행하지 않는다 — 격리라고
            // 진행하면 종료 기수 학생에게 공실 알림이 나간다 (CE-05). 건너뛴 점유는 만료
            // 스케줄러가 정리한다.
            if (alertsCleared) {
                releaseOccupancies(endedCohortId, membershipIds);
            } else {
                log.warn("알림 삭제 실패로 점유 종료(CE-03)를 건너뜁니다 — 순서 역전 방지 (CE-05). cohortId={}",
                        endedCohortId);
            }
        }

        // 4단계 — 실습실 배정 해제 (CE-04). 앞 단계와 무관하게 시도한다. 빠뜨리면 실습실이
        // 배정 상태로 남아 다음 기수 배정이 409로 막힌다.
        try {
            spaceCleanup.unassignLabs(endedCohortId);
        } catch (Exception exception) {
            log.error("기수 종료 실습실 해제(CE-04)에 실패했습니다. 다음 기수 배정이 막힙니다. cohortId={}",
                    endedCohortId, exception);
        }
    }

    private boolean discardAlerts(Long endedCohortId, List<Long> membershipIds) {
        try {
            vacancyAlertService.discardByMemberships(membershipIds);
            return true;
        } catch (Exception exception) {
            log.error("기수 종료 대기 알림 삭제(CE-02)에 실패했습니다. cohortId={}", endedCohortId, exception);
            return false;
        }
    }

    /**
     * 이 기수 멤버십이 점유자인 활성 점유를 전부 종료한다.
     *
     * <p>{@link EndedMembershipOccupancyCleanup#cleanUp}을 그대로 재사용한다 — RELEASED 전이,
     * 참여자 마감, 공실 이벤트 발행까지 명세 08 §2 3단계와 같은 내용이다. 타 기수
     * 대기자에게는 알림이 <b>나가야 한다</b> — 회의실은 공유 자원이고, 이 기수의 신청은
     * 2단계가 이미 지웠다. 종료 기수 사람의 타 점유 참여는 MR-33이 막으므로 여기서
     * 다룰 일이 없다 (명세 08 §5 "발생 불가").</p>
     */
    private void releaseOccupancies(Long endedCohortId, List<Long> membershipIds) {
        List<RoomOccupancyRepository.ActiveOccupancy> occupancies =
                occupancyRepository.findActiveSummariesByOccupierMembershipIds(membershipIds);

        OffsetDateTime endedAt = OffsetDateTime.now(clock);
        int released = 0;
        for (RoomOccupancyRepository.ActiveOccupancy occupancy : occupancies) {
            try {
                if (occupancyCleanup.cleanUp(
                        occupancy.occupierMembershipId(), occupancy.occupierUserId(), endedAt)) {
                    released++;
                }
            } catch (Exception exception) {
                // 한 건의 실패가 나머지를 막지 않는다. 남은 점유는 만료 스케줄러가 받친다.
                log.error("기수 종료 점유 정리(CE-03)에 실패했습니다. occupancyId={}",
                        occupancy.id(), exception);
            }
        }
        if (released > 0) {
            log.info("기수 종료로 점유를 정리했습니다. cohortId={}, 종료={}건", endedCohortId, released);
        }
    }
}
