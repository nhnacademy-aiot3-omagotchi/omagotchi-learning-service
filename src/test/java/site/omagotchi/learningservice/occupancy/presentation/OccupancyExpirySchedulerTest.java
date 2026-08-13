package site.omagotchi.learningservice.occupancy.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * 만료 정리 진입점.
 *
 * <p>이 Class가 소유한 판단은 "실패를 어떻게 다룰지" 하나뿐이다 — 정리 규칙 자체는
 * Application의 것이고 {@code RoomOccupancyLifecycleServiceTest}가 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OccupancyExpirySchedulerTest {

    @Mock
    private RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    @InjectMocks
    private OccupancyExpiryScheduler occupancyExpiryScheduler;

    @Test
    @DisplayName("주기 실행은 임박 알림과 만료 정리를 각각 위임한다.")
    void scheduledRunDelegatesToExpireAll() {
        given(roomOccupancyLifecycleService.sendExpiryReminders()).willReturn(1);
        given(roomOccupancyLifecycleService.expireAll()).willReturn(2);

        occupancyExpiryScheduler.expireStaleOccupancies();

        verify(roomOccupancyLifecycleService).sendExpiryReminders();
        verify(roomOccupancyLifecycleService).expireAll();
    }

    /**
     * 예외를 밖으로 내보내지 않는다.
     *
     * <p>정리는 어차피 다음 주기가 다시 하므로, 여기서 던져 봐야 Spring 내부 Logger에
     * 추적하기 어려운 형태로 남을 뿐이다. 원인을 이 경계에서 한 번 기록하는 것이
     * 유일하게 의미 있는 처리다 (04-error-handling §5, 비 HTTP 실패).</p>
     */
    @Test
    @DisplayName("정리에 실패해도 예외를 밖으로 던지지 않는다.")
    void doesNotPropagateExceptionWhenExpiryFails() {
        willThrow(new IllegalStateException("DB 연결 실패"))
                .given(roomOccupancyLifecycleService).expireAll();

        assertThatCode(occupancyExpiryScheduler::expireStaleOccupancies)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("임박 알림이 실패해도 만료 정리는 계속 실행한다.")
    void expiryReminderFailureDoesNotBlockExpiration() {
        willThrow(new IllegalStateException("알림 실패"))
                .given(roomOccupancyLifecycleService).sendExpiryReminders();

        assertThatCode(occupancyExpiryScheduler::expireStaleOccupancies)
                .doesNotThrowAnyException();

        verify(roomOccupancyLifecycleService).expireAll();
    }
}
