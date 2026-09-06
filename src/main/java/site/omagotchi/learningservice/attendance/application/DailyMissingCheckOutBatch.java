package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.result.DailyMissingCheckOutTarget;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.result.DailyAttendanceClosingPolicyView;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 기수별 예정 종료 시각을 지난 ACTIVE 소속의 미퇴실 출결을 마감한다.
 *
 * <p>마감 시각은 각 출결의 {@code attendanceDate + scheduledEndTime + 유예}이며,
 * 배치 실행 시각이 아니다. 그래서 서버가 늦게 떠도 그날의 마감 시각으로 닫혀
 * 체류가 실행 지연만큼 늘어나지 않는다. 유예 길이와 그 근거는
 * {@link DailyAttendanceClosingPolicyView}에 있다.</p>
 *
 * <p>소속 종료 스윕과 같은 미해결 출결에서 출발하지만, 현재 ACTIVE인
 * 소속만 마감한다. 종료 소속의 실제 {@code endedAt}을 정책 시각으로
 * 덮어쓰지 않기 위함이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyMissingCheckOutBatch {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CohortAttendancePolicyService attendancePolicyService;
    private final MissingCheckOutFinalizer missingCheckOutFinalizer;
    private final Clock clock;

    /**
     * 미해결 출결을 ID 커서로 전체 순회한다. 건별 마감은 독립 Transaction이다.
     *
     * @return 이번 실행에서 체류 또는 출결 상태를 실제로 변경한 출결 수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int closeDueAttendances(int batchSize) {
        long cursor = 0L;
        int dueAttendances = 0;
        int closedAttendances = 0;
        Instant now = clock.instant();

        while (true) {
            List<DailyMissingCheckOutTarget> targets = attendanceRecordRepository
                    .findDailyMissingCheckOutTargetsAfter(cursor, batchSize);
            if (targets.isEmpty()) {
                break;
            }

            Map<Long, DailyAttendanceClosingPolicyView> policies = attendancePolicyService
                    .findActiveDailyClosingPolicies(targets.stream()
                            .map(DailyMissingCheckOutTarget::cohortMembershipId)
                            .distinct()
                            .toList());

            for (DailyMissingCheckOutTarget target : targets) {
                DailyAttendanceClosingPolicyView policy = policies.get(
                        target.cohortMembershipId()
                );
                if (policy == null) {
                    continue;
                }

                Instant deadline;
                try {
                    deadline = policy.closingAt(target.attendanceDate());
                } catch (DateTimeException exception) {
                    log.error("일일 미퇴실 마감 정책이 잘못되어 대상을 건너뜁니다. "
                                    + "attendanceId={}, membershipId={}, timezone={}",
                            target.attendanceId(),
                            target.cohortMembershipId(),
                            policy.timezone(),
                            exception);
                    continue;
                }
                if (now.isBefore(deadline)) {
                    continue;
                }

                dueAttendances++;
                closedAttendances += finalizeOne(target, deadline) ? 1 : 0;
            }

            long nextCursor = targets.getLast().attendanceId();
            if (nextCursor <= cursor) {
                log.error("일일 미퇴실 배치 커서가 전진하지 않아 순회를 중단합니다. "
                                + "cursor={}, nextCursor={}",
                        cursor, nextCursor);
                break;
            }
            cursor = nextCursor;
            if (targets.size() < batchSize) {
                break;
            }
        }

        report(dueAttendances, closedAttendances);
        return closedAttendances;
    }

    private boolean finalizeOne(DailyMissingCheckOutTarget target, Instant deadline) {
        try {
            return missingCheckOutFinalizer.finalizeDaily(
                    target.attendanceId(),
                    target.cohortMembershipId(),
                    deadline
            );
        } catch (Exception exception) {
            log.error("일일 미퇴실 마감에 실패했습니다. attendanceId={}, membershipId={}",
                    target.attendanceId(), target.cohortMembershipId(), exception);
            return false;
        }
    }

    private void report(int dueAttendances, int closedAttendances) {
        if (closedAttendances > 0) {
            log.info("일일 미퇴실 배치가 출결 {}건을 마감했습니다. 마감 대상={} 건",
                    closedAttendances, dueAttendances);
            return;
        }
        log.debug("일일 미퇴실 배치: 마감 대상={} 건, 변경=0건", dueAttendances);
    }
}
