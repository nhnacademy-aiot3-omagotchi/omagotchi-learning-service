package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.EndedMembershipAttendanceCleanup;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 개별 소속 종료 시 점유·회의를 먼저 정리한 뒤 남은 출결·체류를 마감한다.
 *
 * <p>둘을 서로 다른 비동기 리스너로 두면 실행 순서가 보장되지 않는다. 하나의 조정
 * 서비스에서 순서를 고정하되, 각 단계는 자기 Transaction을 사용하고 실패는 격리한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndedMembershipPresenceCleanup {

    private final EndedMembershipOccupancyCleanup occupancyCleanup;
    private final EndedMembershipAttendanceCleanup attendanceCleanup;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void cleanUp(Long membershipId, UUID userId, OffsetDateTime endedAt) {
        try {
            occupancyCleanup.cleanUp(membershipId, userId, endedAt);
        } catch (Exception exception) {
            log.error("소속 종료 점유·회의 정리에 실패했습니다. membershipId={}",
                    membershipId, exception);
        }

        try {
            attendanceCleanup.cleanUp(membershipId);
        } catch (Exception exception) {
            log.error("소속 종료 출결·체류 정리에 실패했습니다. membershipId={}",
                    membershipId, exception);
        }
    }
}
