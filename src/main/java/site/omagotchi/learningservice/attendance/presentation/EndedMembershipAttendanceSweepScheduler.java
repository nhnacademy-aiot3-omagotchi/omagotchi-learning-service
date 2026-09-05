package site.omagotchi.learningservice.attendance.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.attendance.application.EndedMembershipAttendanceSweep;

/** 종료 소속의 미해결 출결·체류를 주기적으로 복구하는 외부 진입점. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndedMembershipAttendanceSweepScheduler {

    private final EndedMembershipAttendanceSweep attendanceSweep;

    @Value("${omagotchi.attendance.membership-sweep.batch-size:200}")
    private int batchSize;

    /**
     * 기존 점유 스윕과 독립적으로 실행한다.
     *
     * <p>출결 스윕이 먼저 열린 MEETING을 만나면 안전하게 건너뛰고, 점유 스윕이 회의를
     * 닫은 뒤 다음 실행에서 다시 발견한다. 따라서 두 Scheduler의 실행 순서에 의존하지
     * 않는다.</p>
     */
    @Scheduled(
            fixedDelayString = "${omagotchi.attendance.membership-sweep.fixed-delay:300000}",
            initialDelayString = "${omagotchi.attendance.membership-sweep.initial-delay:60000}"
    )
    public void sweepEndedMemberships() {
        try {
            attendanceSweep.sweep(batchSize);
        } catch (Exception exception) {
            log.error("종료 소속 출결 정합성 스윕에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }
    }
}
