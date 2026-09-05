package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;

import java.util.List;

/** 기수 종료 시 정리가 필요한 소속만 골라 출결 마감을 위임한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CohortEndedAttendanceCleanup {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EndedMembershipAttendanceCleanup membershipAttendanceCleanup;

    /**
     * 소속별 결과를 격리해 한 소속의 실패가 같은 기수의 나머지 출결을 막지 않게 한다.
     * 각 소속 내부의 출결은 {@link EndedMembershipAttendanceCleanup}이 건별로 다시 격리한다.
     *
     * @return 이번 호출로 마감한 출결 수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int closeAllByCohort(List<Long> membershipIds) {
        if (membershipIds.isEmpty()) {
            return 0;
        }

        List<Long> targetMembershipIds = attendanceRecordRepository
                .findDistinctEndCleanupMembershipIds(membershipIds);
        int cleaned = 0;
        for (Long membershipId : targetMembershipIds) {
            try {
                cleaned += membershipAttendanceCleanup.cleanUp(membershipId);
            } catch (Exception exception) {
                log.error("기수 종료 출결 정리에 실패했습니다. membershipId={}",
                        membershipId, exception);
            }
        }
        return cleaned;
    }
}
