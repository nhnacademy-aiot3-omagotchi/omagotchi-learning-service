package site.omagotchi.learningservice.attendance.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.attendance.application.EndedMembershipAttendanceSweep;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("종료 소속 출결 스윕 스케줄러")
class EndedMembershipAttendanceSweepSchedulerTest {

    private static final int BATCH_SIZE = 200;

    @Mock
    private EndedMembershipAttendanceSweep attendanceSweep;

    @InjectMocks
    private EndedMembershipAttendanceSweepScheduler scheduler;

    @BeforeEach
    void setBatchSize() {
        ReflectionTestUtils.setField(scheduler, "batchSize", BATCH_SIZE);
    }

    @Test
    @DisplayName("설정된 배치 크기로 출결 스윕을 실행한다")
    void delegatesWithConfiguredBatchSize() {
        scheduler.sweepEndedMemberships();

        verify(attendanceSweep).sweep(BATCH_SIZE);
    }

    @Test
    @DisplayName("스윕 경계의 실패를 삼켜 다음 주기 실행을 보존한다")
    void isolatesSweepFailure() {
        willThrow(new IllegalStateException("조회 실패"))
                .given(attendanceSweep).sweep(BATCH_SIZE);

        assertThatCode(scheduler::sweepEndedMemberships).doesNotThrowAnyException();
    }
}
