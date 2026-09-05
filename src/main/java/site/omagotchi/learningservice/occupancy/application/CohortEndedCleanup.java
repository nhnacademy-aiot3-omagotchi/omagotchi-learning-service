package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.CohortEndedAttendanceCleanup;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.sensor.application.CohortEndedSensorCleanup;
import site.omagotchi.learningservice.space.application.CohortEndedSpaceCleanup;
import site.omagotchi.learningservice.team.application.CohortEndedTeamCleanup;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 기수 종료 시 팀·알림·점유·출결·센서·공간을 정해진 순서로 정리한다 (명세 08).
 * 진입점은 {@link site.omagotchi.learningservice.occupancy.presentation.CohortClosedListener}.
 *
 * <p>단일 클래스·단계별 격리·순서 예외의 근거는 ADR space-team/0015. 요지만 남긴다 —
 * <b>대기 알림 삭제(CE-02)는 점유 종료(CE-03)보다 반드시 먼저다.</b> 뒤집히면 CE-03의
 * 공실 발송이 방금 종료된 기수의 신청을 대기 중으로 보고 그 학생들에게 알림을 보낸다.
 * 그래서 CE-02 실패 시에는(단계별 격리 원칙의 유일한 예외로) CE-03을 건너뛴다.</p>
 *
 * <p>멤버십 목록은 기수 파트에 묻고({@code findMembershipIds}), <b>상태를 가리지 않는다</b>
 * — 종료 훅이 도는 시점에는 기수 파트가 멤버십을 이미 ENDED로 바꿨을 수 있다. 활성으로
 * 좁히면 대상을 하나도 찾지 못해 활성 점유와 대기 신청이 잔존한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CohortEndedCleanup {

    private final CohortEndedTeamCleanup teamCleanup;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final VacancyAlertService vacancyAlertService;
    private final RoomOccupancyRepository occupancyRepository;
    private final EndedMembershipOccupancyCleanup occupancyCleanup;
    private final CohortEndedAttendanceCleanup attendanceCleanup;
    private final CohortEndedSensorCleanup sensorCleanup;
    private final CohortEndedSpaceCleanup spaceCleanup;

    /**
     * 종료된 기수를 6단계로 정리한다. 같은 기수에 두 번 호출해도 안전하다 — 각 단계가
     * 조건부 연산이라 두 번째는 대상이 없다 (명세 08 §5 "훅 중복 수신").
     *
     * <p><b>이 Method에 Transaction이 없는 것이 의도다.</b> 단계마다 자기 Transaction을
     * 열어야 부분 실패가 격리된다 — 여기에 하나로 두르면 마지막 단계의 실패가 팀 해체까지
     * 되돌리고, 기수 종료 자체를 막게 된다 (명세 08 §5 "기수 종료는 롤백하지 않음").</p>
     *
     * @param endedCohortId 종료된 기수
     * @param closedAt      이벤트에 기록된 기수 종료 시각
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void cleanUp(Long endedCohortId, OffsetDateTime closedAt) {

        // 1단계 — 팀 정리 (CE-01). 실패해도 나머지 단계와 무관하다.
        try {
            teamCleanup.disbandAllByCohort(endedCohortId);
        } catch (Exception exception) {
            log.error("기수 종료 팀 정리(CE-01)에 실패했습니다. cohortId={}", endedCohortId, exception);
        }

        // CE-02·03과 출결 정리의 대상은 멤버십 조인으로만 나온다 (명세 08 §1).
        // 이 조회가 실패하면 셋 모두 대상을 특정할 수 없으므로 함께 건너뛰고,
        // 기수 단위인 센서·공간 정리는 계속 진행한다.
        List<Long> membershipIds;
        try {
            membershipIds = cohortMembershipQueryService.findMembershipIds(endedCohortId);
        } catch (Exception exception) {
            log.error("기수 종료 멤버십 조회에 실패해 알림·점유·출결 정리를 건너뜁니다. cohortId={}",
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
                releaseOccupancies(endedCohortId, membershipIds, closedAt);
            } else {
                log.warn("알림 삭제 실패로 점유 종료(CE-03)를 건너뜁니다 — 순서 역전 방지 (CE-05). cohortId={}",
                        endedCohortId);
            }

            // 4단계 — 출결·체류 마감. CE-02 실패로 점유 정리를 건너뛰었더라도 일반
            // PRESENT/AWAY 체류는 독립적으로 닫는다. 열린 MEETING은 출결 정리 내부에서
            // 건너뛰고 정합성 스윕에 맡긴다.
            //
            // 이 단계만 소속이 ENDED인 건을 대상으로 삼는다 — 앞 단계들이 상태를 가리지
            // 않는 것과 반대다. 근거는 MissingCheckOutFinalizer#finalizeOne javadoc.
            try {
                attendanceCleanup.closeAllByCohort(membershipIds, closedAt);
            } catch (Exception exception) {
                log.error("기수 종료 출결·체류 정리에 실패했습니다. cohortId={}",
                        endedCohortId, exception);
            }
        }

        // 5단계 — 센서 회수 (CE-05). 6단계보다 반드시 먼저다 — 공간의 cohort_id가 지워진
        // 뒤에는 대상 센서를 특정할 수 없고, 회수되지 못한 센서는 룰을 계속 발화시킨다.
        //
        // 실패해도 6단계를 건너뛰지 않는다. cohort_id가 남으면 SensorDeviceService#claim의
        // 고아 판정(findCohortId().isEmpty())을 통과하지 못해 다음 기수가 인계할 수 없고,
        // 이 기수의 멤버십은 이미 종료되어 아무도 손댈 수 없는 상태가 된다. CE-04에는 재시도
        // 수단이 없다 (CohortClosedListener javadoc). 회수 대상 EUI는 CE-05가 미리 남긴다.
        try {
            sensorCleanup.deactivateSensors(endedCohortId);
        } catch (Exception exception) {
            log.error("기수 종료 센서 회수(CE-05)에 실패했습니다. 해당 센서의 룰이 계속 발화합니다. cohortId={}",
                    endedCohortId, exception);
        }

        // 6단계 — 공간 관리 주체 해제 (CE-04). 앞 단계와 무관하게 시도한다.
        try {
            spaceCleanup.unassignSpaces(endedCohortId);
        } catch (Exception exception) {
            log.error("기수 종료 공간 관리 주체 해제(CE-04)에 실패했습니다. 해당 공간이 동결됩니다. cohortId={}",
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
    private void releaseOccupancies(
            Long endedCohortId,
            List<Long> membershipIds,
            OffsetDateTime endedAt
    ) {
        List<RoomOccupancyRepository.ActiveOccupancy> occupancies =
                occupancyRepository.findActiveSummariesByOccupierMembershipIds(membershipIds);

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
