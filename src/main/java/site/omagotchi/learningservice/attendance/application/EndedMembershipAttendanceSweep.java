package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.result.AttendanceCleanupTarget;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 종료된 소속의 출결 정리가 유실됐을 때 미해결 출결·체류를 되찾는 정합성 스윕.
 *
 * <p><b>기존 점유 스윕과 탐색 기준이 다르다.</b> 점유 스윕은 열린 참여 행에서 출발하므로
 * 점유·MEETING을 닫은 다음에는 그 소속을 다시 발견할 수 없다. 이 스윕은 미해결
 * {@code attendance_records}에서 직접 출발해, 참여가 이미 닫힌 뒤 남은
 * {@code MISSING_CHECK_OUT} 확정까지 책임진다.</p>
 *
 * <p><b>기수 테이블을 직접 조인하지 않는다.</b> 출결 저장소는 미해결 후보만 반환하고,
 * 소속이 실제로 끝났는지는 {@link CohortMembershipQueryService}의 공개 계약에 묻는다.
 * 활성 소속을 정리하는 것이 이 배치의 가장 위험한 실패이므로, 조회 결과에 명시된 ENDED
 * 소속만 {@link EndedMembershipAttendanceCleanup}에 넘긴다. PENDING·REJECTED는 실제
 * 마감의 ENDED 잠금 계약과 일치하지 않으므로 대상이 아니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndedMembershipAttendanceSweep {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final EndedMembershipAttendanceCleanup attendanceCleanup;

    /**
     * 미해결 출결을 ID 커서로 순회하고 종료 소속의 출결만 마감한다.
     *
     * <p><b>이 Method에 Transaction이 없는 것이 의도다.</b> 실제 마감은
     * {@link EndedMembershipAttendanceCleanup} 아래의 건별 Transaction에서 수행한다.
     * 실패한 행은 미해결 상태로 남아 다음 실행의 커서 순회에서 다시 발견된다.</p>
     *
     * <p>같은 소속에 여러 날짜의 미해결 출결이 있어도 한 실행에서는 소속 정리를 한 번만
     * 호출한다. 소속 단위 정리가 그 소속의 전체 대상을 ID 순서로 처리하기 때문이다.</p>
     *
     * @param batchSize 한 배치에서 조회할 미해결 출결 수
     * @return 이번 실행으로 실제 변경한 출결 수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int sweep(int batchSize) {
        long cursor = 0L;
        int detectedMemberships = 0;
        int cleanedAttendances = 0;
        Set<Long> attemptedMembershipIds = new LinkedHashSet<>();

        while (true) {
            List<AttendanceCleanupTarget> targets = attendanceRecordRepository
                    .findEndCleanupTargetsAfter(cursor, batchSize);
            if (targets.isEmpty()) {
                break;
            }

            List<Long> membershipIds = targets.stream()
                    .map(AttendanceCleanupTarget::cohortMembershipId)
                    .distinct()
                    .toList();
            Set<Long> endedMembershipIds = cohortMembershipQueryService
                    .findEndedMemberships(membershipIds)
                    .keySet();

            for (Long membershipId : membershipIds) {
                if (!endedMembershipIds.contains(membershipId)
                        || !attemptedMembershipIds.add(membershipId)) {
                    continue;
                }
                detectedMemberships++;
                cleanedAttendances += cleanUp(membershipId);
            }

            long nextCursor = targets.getLast().attendanceId();
            if (nextCursor <= cursor) {
                log.error("출결 정합성 스윕 커서가 전진하지 않아 순회를 중단합니다. "
                                + "cursor={}, nextCursor={}",
                        cursor, nextCursor);
                break;
            }
            cursor = nextCursor;
            if (targets.size() < batchSize) {
                break;
            }
        }

        report(detectedMemberships, cleanedAttendances);
        return cleanedAttendances;
    }

    /** 한 소속의 실패를 격리한다. 미해결 행이 남으므로 다음 주기에 다시 시도된다. */
    private int cleanUp(Long membershipId) {
        try {
            return attendanceCleanup.cleanUp(membershipId);
        } catch (Exception exception) {
            log.error("종료 소속 출결 정합성 복구에 실패했습니다. membershipId={}",
                    membershipId, exception);
            return 0;
        }
    }

    /** 정상 기대값인 검출 0건과 실제 복구량을 구분해 운영 신호로 남긴다. */
    private void report(int detectedMemberships, int cleanedAttendances) {
        if (detectedMemberships == 0) {
            log.debug("출결 정합성 스윕: 종료 소속의 미해결 출결이 없습니다.");
            return;
        }

        log.warn("출결 정합성 스윕이 종료 소속 {}건을 검출해 출결 {}건을 정리했습니다. "
                        + "검출이 계속 0이 아니면 소속 종료 이벤트 경로를 확인해야 합니다.",
                detectedMemberships, cleanedAttendances);
    }
}
