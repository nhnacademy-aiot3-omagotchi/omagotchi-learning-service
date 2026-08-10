package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 만료 점유 한 건의 종료 처리 (스케줄러 #9).
 *
 * <p>여기서 고정하는 것은 <b>전이 실패 시 아무것도 하지 않는다</b>이다. 조회와 전이 사이에
 * 연장·반납이 일어나거나 다른 인스턴스가 먼저 처리한 상황이며, 그때 참여자를 닫으면
 * 사용 중인 회의의 참여자가 사라지고 알림까지 잘못 나간다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OccupancyExpirationTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final Long OCCUPANCY_ID = 7L;
    private static final Long SPACE_ID = 1L;

    @Mock
    private RoomOccupancyRepository occupancyRepository;

    @Mock
    private OccupancyParticipantRepository participantRepository;

    @Mock
    private OccupancyEventPublisher eventPublisher;

    @InjectMocks
    private OccupancyExpiration occupancyExpiration;

    @Test
    @DisplayName("전이에 성공하면 참여자를 마감하고 공실을 알린다.")
    void closesParticipantsAndPublishesVacatedEventOnSuccess() {
        OffsetDateTime endedAt = NOW.minusMinutes(5);
        given(occupancyRepository.expire(OCCUPANCY_ID, NOW)).willReturn(true);

        assertThat(occupancyExpiration.expire(candidate(endedAt), NOW)).isTrue();

        verify(participantRepository).closeAllActiveByOccupancyId(OCCUPANCY_ID, endedAt);
        verify(eventPublisher).publishRoomVacated(
                new RoomVacatedEvent(SPACE_ID, OCCUPANCY_ID, endedAt));
    }

    /**
     * 전이가 0행이면 연장·반납·타 인스턴스 선처리 중 하나다.
     *
     * <p>연장된 경우가 특히 위험하다 — 참여자를 닫으면 <b>지금 회의 중인 사람들이
     * 명단에서 사라지고</b>, 대기자에게는 쓰고 있는 방이 비었다고 알린다.</p>
     */
    @Test
    @DisplayName("전이되지 않으면 참여자도 알림도 건드리지 않는다.")
    void doesNothingWhenTransitionFails() {
        given(occupancyRepository.expire(OCCUPANCY_ID, NOW)).willReturn(false);

        assertThat(occupancyExpiration.expire(candidate(NOW.minusMinutes(5)), NOW)).isFalse();

        verify(participantRepository, never()).closeAllActiveByOccupancyId(any(), any());
        verify(eventPublisher, never()).publishRoomVacated(any());
    }

    /**
     * 마감·공실 시각은 처리 시각이 아니라 점유의 만료 시각이다. 스케줄러 주기만큼 늦게
     * 발견했을 뿐 실제로 끝난 것은 {@code expires_at} 시점이라, 지금 시각을 찍으면
     * 참여 시간이 길게 집계되고 "언제 비었는지"도 밀린다.
     */
    @Test
    @DisplayName("마감과 공실 시각은 처리 시각이 아니라 만료 시각이다.")
    void closedAtIsExpiryTimeNotProcessingTime() {
        OffsetDateTime endedAt = NOW.minusMinutes(5);
        given(occupancyRepository.expire(OCCUPANCY_ID, NOW)).willReturn(true);

        occupancyExpiration.expire(candidate(endedAt), NOW);

        verify(participantRepository, never()).closeAllActiveByOccupancyId(OCCUPANCY_ID, NOW);
        verify(eventPublisher, never()).publishRoomVacated(
                new RoomVacatedEvent(SPACE_ID, OCCUPANCY_ID, NOW));
    }

    private RoomOccupancyRepository.ExpiredOccupancy candidate(OffsetDateTime endedAt) {
        return new RoomOccupancyRepository.ExpiredOccupancy(OCCUPANCY_ID, SPACE_ID, endedAt);
    }
}
