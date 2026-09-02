package site.omagotchi.learningservice.occupancy.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.AttendanceErrorCode;
import site.omagotchi.learningservice.attendance.application.AttendancePresenceQueryService;
import site.omagotchi.learningservice.attendance.application.PresenceTransitionService;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("점유와 회의 체류 동기화")
class MeetingPresenceCoordinatorTest {

    private static final Long OCCUPANCY_ID = 50L;
    private static final Long MEETING_ID = 70L;
    private static final OffsetDateTime AT = OffsetDateTime.of(
            2026, 8, 31, 14, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private AttendancePresenceQueryService attendancePresenceQueryService;

    @Mock
    private PresenceTransitionService presenceTransitionService;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private OccupancyParticipantRepository participantRepository;

    @InjectMocks
    private MeetingPresenceCoordinator coordinator;

    @Test
    @DisplayName("점유 종료는 출결 ID 오름차순으로 회의를 나간 뒤 참여 행을 닫는다")
    void leavesMeetingsInAttendanceIdOrderBeforeClosingParticipants() {
        var first = participant(101L);
        var second = participant(102L);
        when(participantRepository.findActiveParticipantsByOccupancyId(OCCUPANCY_ID))
                .thenReturn(List.of(first, second));
        when(attendancePresenceQueryService.findOpenMeetingPresencesByMembershipIds(
                List.of(101L, 102L), MEETING_ID))
                .thenReturn(Map.of(
                        101L, presence(20L, 101L),
                        102L, presence(10L, 102L)
                ));
        when(cohortMembershipQueryService.findInactiveMembershipIds(List.of(101L, 102L)))
                .thenReturn(Set.of());
        when(participantRepository.closeAllActiveByOccupancyId(OCCUPANCY_ID, AT)).thenReturn(2);

        assertThat(coordinator.leaveAll(OCCUPANCY_ID, MEETING_ID, AT)).isEqualTo(2);

        InOrder order = inOrder(presenceTransitionService, participantRepository);
        order.verify(presenceTransitionService).leaveMeeting(10L, 102L, MEETING_ID, AT.toInstant());
        order.verify(presenceTransitionService).leaveMeeting(20L, 101L, MEETING_ID, AT.toInstant());
        order.verify(participantRepository).closeAllActiveByOccupancyId(OCCUPANCY_ID, AT);
    }

    @Test
    @DisplayName("종료된 소속은 이전 LAB으로 복귀시키지 않고 MEETING만 닫는다")
    void closesMeetingWithoutReturnForInactiveMembership() {
        var participant = participant(101L);
        when(participantRepository.findActiveParticipantsByOccupancyId(OCCUPANCY_ID))
                .thenReturn(List.of(participant));
        when(attendancePresenceQueryService.findOpenMeetingPresencesByMembershipIds(
                List.of(101L), MEETING_ID))
                .thenReturn(Map.of(101L, presence(10L, 101L)));
        when(cohortMembershipQueryService.findInactiveMembershipIds(List.of(101L)))
                .thenReturn(Set.of(101L));

        coordinator.leaveAll(OCCUPANCY_ID, MEETING_ID, AT);

        verify(presenceTransitionService)
                .closeMeetingWithoutReturn(10L, 101L, MEETING_ID, AT.toInstant());
        verify(presenceTransitionService, never()).leaveMeeting(10L, 101L, MEETING_ID, AT.toInstant());
    }

    @Test
    @DisplayName("참여 소속의 열린 출결이 없으면 참여 행도 닫지 않고 실패한다")
    void rejectsPartialCloseWhenPresenceIsMissing() {
        var participant = participant(101L);
        when(participantRepository.findActiveParticipantsByOccupancyId(OCCUPANCY_ID))
                .thenReturn(List.of(participant));
        when(attendancePresenceQueryService.findOpenMeetingPresencesByMembershipIds(
                List.of(101L), MEETING_ID))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> coordinator.leaveAll(OCCUPANCY_ID, MEETING_ID, AT))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isSameAs(AttendanceErrorCode.PRESENCE_ACTIVE_INTERVAL_REQUIRED));

        verify(participantRepository, never()).closeAllActiveByOccupancyId(OCCUPANCY_ID, AT);
    }

    @Test
    @DisplayName("소속 종료 참여 정리는 정확한 소속의 회의만 닫고 새 PRESENT를 만들지 않는다")
    void closesExactEndedMembershipParticipation() {
        OccupancyParticipant participant = OccupancyParticipant.join(
                OCCUPANCY_ID, 101L, UUID.randomUUID(), AT.minusHours(1));
        when(participantRepository.findActiveByCohortMembershipId(101L))
                .thenReturn(Optional.of(participant));
        when(attendancePresenceQueryService.findOpenMeetingPresencesByMembershipIds(
                List.of(101L), null))
                .thenReturn(Map.of(101L, presence(10L, 101L)));

        assertThat(coordinator.closeEndedMembership(101L, AT)).isTrue();

        verify(presenceTransitionService)
                .closeAnyMeetingWithoutReturn(10L, 101L, AT.toInstant());
        assertThat(participant.getLeftAt()).isEqualTo(AT);
    }

    @Test
    @DisplayName("같은 회의의 열린 구간이 있으면 활성 참여 재요청은 체류를 다시 전환하지 않는다")
    void keepsExistingMeetingOnActiveParticipantRetry() {
        when(attendancePresenceQueryService.findOpenMeetingPresencesByMembershipIds(
                List.of(101L), MEETING_ID))
                .thenReturn(Map.of(101L, presence(10L, 101L)));

        coordinator.ensureEntered(101L, MEETING_ID, AT);

        verify(attendancePresenceQueryService, never())
                .findOpenPresencesByMembershipIds(List.of(101L));
        verify(presenceTransitionService, never())
                .enterMeeting(10L, 101L, MEETING_ID, AT.toInstant());
    }

    @Test
    @DisplayName("참여 행만 활성이고 회의 구간이 없으면 최신 체류를 같은 회의로 복구한다")
    void repairsMissingMeetingOnActiveParticipantRetry() {
        when(attendancePresenceQueryService.findOpenMeetingPresencesByMembershipIds(
                List.of(101L), MEETING_ID))
                .thenReturn(Map.of());
        when(attendancePresenceQueryService.findOpenPresencesByMembershipIds(List.of(101L)))
                .thenReturn(Map.of(101L, presence(10L, 101L)));

        coordinator.ensureEntered(101L, MEETING_ID, AT);

        verify(presenceTransitionService)
                .enterMeeting(10L, 101L, MEETING_ID, AT.toInstant());
    }

    private OccupancyParticipantRepository.ActiveParticipant participant(Long membershipId) {
        return new OccupancyParticipantRepository.ActiveParticipant(membershipId, UUID.randomUUID());
    }

    private OpenPresenceView presence(Long attendanceId, Long membershipId) {
        return new OpenPresenceView(attendanceId, membershipId, Instant.parse("2026-08-31T04:00:00Z"));
    }
}
