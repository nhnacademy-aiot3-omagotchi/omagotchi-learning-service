package site.omagotchi.learningservice.attendance.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.attendance.application.DailyMissingCheckOutBatch;

/** 기수별 출결일 종료 후 미퇴실을 마감하는 외부 진입점. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyMissingCheckOutScheduler {

    private final DailyMissingCheckOutBatch batch;

    @Value("${omagotchi.attendance.daily-close.batch-size:200}")
    private int batchSize;

    /**
     * 기본 1분 주기로 돌아 예정 종료 직후 최대 1분 내에 마감한다.
     * 중복 실행은 소속·출결 행 잠금과 멱등 상태 전이로 흡수한다.
     */
    @Scheduled(
            fixedDelayString = "${omagotchi.attendance.daily-close.fixed-delay:60000}",
            initialDelayString = "${omagotchi.attendance.daily-close.initial-delay:30000}"
    )
    public void closeDailyMissingCheckOuts() {
        try {
            batch.closeDueAttendances(batchSize);
        } catch (Exception exception) {
            log.error("일일 미퇴실 배치에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }
    }
}
