package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;
import site.omagotchi.learningservice.space.application.SpaceNameQueryService;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OccupancyExpiryReminderTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final Long OCCUPANCY_ID = 100L;
    private static final Long SPACE_ID = 1L;
    private static final String SPACE_NAME = "테스트 회의실";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private RoomOccupancyRepository occupancyRepository;

    @Mock
    private SpaceNameQueryService spaceNameQueryService;

    @Mock
    private OccupancyReminderSender sender;

    private OccupancyExpiryReminder expiryReminder;

    @BeforeEach
    void setUp() {
        expiryReminder = new OccupancyExpiryReminder(
                occupancyRepository, spaceNameQueryService, Clock.fixed(NOW.toInstant(), NOW.getOffset()));
    }

    /**
     * 이름 조회 실패가 알림 자체를 막으면 안 된다. 이름을 못 찾았다고 건너뛰면 점유자는
     * 곧 방이 회수된다는 사실을 모른 채 쫓겨난다.
     */
    @Test
    @DisplayName("공간 이름을 찾지 못해도 식별자로 대체해 발송한다.")
    void fallsBackToSpaceIdWhenNameIsMissing() {
        RoomOccupancy occupancy = occupancy(NOW.plusMinutes(10));
        RoomOccupancyRepository.ExpiringOccupancy candidate = candidate(occupancy);
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy));
        given(spaceNameQueryService.findName(SPACE_ID)).willReturn(Optional.empty());

        assertThat(expiryReminder.send(candidate, sender)).isTrue();

        verify(sender).sendExpiryReminder(new OccupancyReminderSender.ExpiryReminder(
                OCCUPANCY_ID, SPACE_ID, "공간 " + SPACE_ID, USER_ID, NOW.plusMinutes(10)));
    }

    @Test
    @DisplayName("실제 발송 성공 뒤에만 reminderSentAt을 기록한다.")
    void marksReminderOnlyAfterSuccessfulSend() {
        RoomOccupancy occupancy = occupancy(NOW.plusMinutes(10));
        RoomOccupancyRepository.ExpiringOccupancy candidate = candidate(occupancy);
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy));
        given(spaceNameQueryService.findName(SPACE_ID)).willReturn(Optional.of(SPACE_NAME));

        assertThat(expiryReminder.send(candidate, sender)).isTrue();

        verify(sender).sendExpiryReminder(new OccupancyReminderSender.ExpiryReminder(
                OCCUPANCY_ID, SPACE_ID, SPACE_NAME, USER_ID, NOW.plusMinutes(10)));
        assertThat(occupancy.getReminderSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("실제 발송이 실패하면 reminderSentAt을 기록하지 않는다.")
    void leavesReminderPendingWhenSendFails() {
        RoomOccupancy occupancy = occupancy(NOW.plusMinutes(5));
        RoomOccupancyRepository.ExpiringOccupancy candidate = candidate(occupancy);
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy));
        given(spaceNameQueryService.findName(SPACE_ID)).willReturn(Optional.of(SPACE_NAME));
        willThrow(new IllegalStateException("발송 실패"))
                .given(sender).sendExpiryReminder(any());

        assertThatThrownBy(() -> expiryReminder.send(candidate, sender))
                .isInstanceOf(IllegalStateException.class);

        assertThat(occupancy.getReminderSentAt()).isNull();
    }

    @Test
    @DisplayName("발송 실패 뒤 다음 실행에서는 같은 점유를 다시 시도할 수 있다.")
    void retriesSameOccupancyAfterSendFailure() {
        RoomOccupancy occupancy = occupancy(NOW.plusMinutes(5));
        RoomOccupancyRepository.ExpiringOccupancy candidate = candidate(occupancy);
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy));
        given(spaceNameQueryService.findName(SPACE_ID)).willReturn(Optional.of(SPACE_NAME));
        doThrow(new IllegalStateException("첫 발송 실패"))
                .doNothing()
                .when(sender).sendExpiryReminder(any());

        assertThatThrownBy(() -> expiryReminder.send(candidate, sender))
                .isInstanceOf(IllegalStateException.class);
        assertThat(occupancy.getReminderSentAt()).isNull();

        assertThat(expiryReminder.send(candidate, sender)).isTrue();
        assertThat(occupancy.getReminderSentAt()).isEqualTo(NOW);
        verify(sender, times(2)).sendExpiryReminder(any());
    }

    @Test
    @DisplayName("발송 성공 뒤 같은 후보를 다시 처리해도 중복 발송하지 않는다.")
    void doesNotSendSameReminderTwiceAfterSuccess() {
        RoomOccupancy occupancy = occupancy(NOW.plusMinutes(5));
        RoomOccupancyRepository.ExpiringOccupancy candidate = candidate(occupancy);
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy));
        given(spaceNameQueryService.findName(SPACE_ID)).willReturn(Optional.of(SPACE_NAME));

        assertThat(expiryReminder.send(candidate, sender)).isTrue();
        assertThat(expiryReminder.send(candidate, sender)).isFalse();

        verify(sender, times(1)).sendExpiryReminder(any());
        assertThat(occupancy.getReminderSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("후보 조회 뒤 연장되면 예전 만료 시각의 알림을 보내지 않는다.")
    void skipsCandidateExtendedAfterLookup() {
        RoomOccupancy occupancy = occupancy(NOW.plusMinutes(5));
        RoomOccupancyRepository.ExpiringOccupancy staleCandidate = candidate(occupancy);
        occupancy.extend();
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy));

        assertThat(expiryReminder.send(staleCandidate, sender)).isFalse();

        verify(sender, never()).sendExpiryReminder(any());
        assertThat(occupancy.getReminderSentAt()).isNull();
    }

    @Test
    @DisplayName("종료된 점유는 락을 잡은 뒤 다시 걸러낸다.")
    void skipsEndedCandidateAfterLock() {
        RoomOccupancy occupancy = occupancy(NOW.plusMinutes(5));
        RoomOccupancyRepository.ExpiringOccupancy candidate = candidate(occupancy);
        ReflectionTestUtils.setField(occupancy, "status", OccupancyStatus.EXPIRED);
        ReflectionTestUtils.setField(occupancy, "endedAt", occupancy.getExpiresAt());
        given(occupancyRepository.lockById(OCCUPANCY_ID)).willReturn(Optional.of(occupancy));

        assertThat(expiryReminder.send(candidate, sender)).isFalse();

        verify(sender, never()).sendExpiryReminder(any());
    }

    private RoomOccupancy occupancy(OffsetDateTime expiresAt) {
        RoomOccupancy occupancy = RoomOccupancy.start(
                SPACE_ID, 10L, USER_ID, NOW.minusHours(2), expiresAt);
        ReflectionTestUtils.setField(occupancy, "id", OCCUPANCY_ID);
        return occupancy;
    }

    private RoomOccupancyRepository.ExpiringOccupancy candidate(RoomOccupancy occupancy) {
        return new RoomOccupancyRepository.ExpiringOccupancy(
                occupancy.getId(), occupancy.getExpiresAt());
    }
}
