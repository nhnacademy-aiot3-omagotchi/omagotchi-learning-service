package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.AttendanceErrorCode;
import site.omagotchi.learningservice.attendance.application.AttendancePresenceQueryService;
import site.omagotchi.learningservice.attendance.application.PresenceTransitionService;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 점유 참여 행과 출결 체류 구간을 한 트랜잭션에서 함께 전환하는 경계.
 * 여러 출결 행은 항상 ID 오름차순으로 잠근다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MeetingPresenceCoordinator {

    private final AttendancePresenceQueryService attendancePresenceQueryService;
    private final PresenceTransitionService presenceTransitionService;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final OccupancyParticipantRepository participantRepository;

    public void enter(OpenPresenceView presence, Long meetingSpaceId, OffsetDateTime at) {
        presenceTransitionService.enterMeeting(
                presence.attendanceId(),
                presence.cohortMembershipId(),
                meetingSpaceId,
                at.toInstant()
        );
    }

    /**
     * 활성 참여 행의 멱등 재요청을 체류 구간과 다시 맞춘다.
     * 같은 회의의 열린 MEETING이 있으면 그대로 두고, 없을 때만 해당 소속의 최신
     * 열린 구간을 회의로 전환한다.
     */
    public void ensureEntered(Long membershipId, Long meetingSpaceId, OffsetDateTime at) {
        Map<Long, OpenPresenceView> meetings = attendancePresenceQueryService
                .findOpenMeetingPresencesByMembershipIds(
                        List.of(membershipId),
                        meetingSpaceId
                );
        if (meetings.containsKey(membershipId)) {
            return;
        }

        OpenPresenceView presence = requireLatestPresences(List.of(membershipId))
                .get(membershipId);
        enter(presence, meetingSpaceId, at);
    }

    /** 참여자 한 명의 이탈·제외와 체류 복귀를 함께 처리한다. */
    public boolean leaveOne(
            OccupancyParticipant participant,
            Long meetingSpaceId,
            OffsetDateTime at
    ) {
        transitionParticipants(
                List.of(new OccupancyParticipantRepository.ActiveParticipant(
                        participant.getCohortMembershipId(), participant.getUserId())),
                meetingSpaceId,
                at
        );
        return participant.leave(at);
    }

    /** 점유 종료와 함께 활성 참여자 전원을 복귀시키고 참여 행을 닫는다. */
    public int leaveAll(Long occupancyId, Long meetingSpaceId, OffsetDateTime at) {
        List<OccupancyParticipantRepository.ActiveParticipant> participants =
                participantRepository.findActiveParticipantsByOccupancyId(occupancyId);
        if (participants.isEmpty()) {
            return 0;
        }
        transitionParticipants(participants, meetingSpaceId, at);
        return participantRepository.closeAllActiveByOccupancyId(occupancyId, at);
    }

    /** 종료된 소속의 참여는 이전 LAB으로 복귀시키지 않고 MEETING만 닫는다. */
    public boolean closeEndedMembership(Long membershipId, OffsetDateTime at) {
        OccupancyParticipant participant = participantRepository
                .findActiveByCohortMembershipId(membershipId)
                .orElse(null);
        if (participant == null) {
            return false;
        }
        OpenPresenceView presence = requireMeetingPresences(
                List.of(membershipId),
                null
        ).get(membershipId);
        presenceTransitionService.closeAnyMeetingWithoutReturn(
                presence.attendanceId(),
                membershipId,
                at.toInstant()
        );
        return participant.leave(at);
    }

    private void transitionParticipants(
            List<OccupancyParticipantRepository.ActiveParticipant> participants,
            Long meetingSpaceId,
            OffsetDateTime at
    ) {
        List<Long> membershipIds = participants.stream()
                .map(OccupancyParticipantRepository.ActiveParticipant::cohortMembershipId)
                .distinct()
                .toList();
        Map<Long, OpenPresenceView> presences = requireMeetingPresences(
                membershipIds,
                meetingSpaceId
        );
        Set<Long> inactiveMembershipIds = cohortMembershipQueryService
                .findInactiveMembershipIds(membershipIds);

        presences.values().stream()
                .sorted(Comparator.comparing(OpenPresenceView::attendanceId))
                .forEach(presence -> {
                    if (inactiveMembershipIds.contains(presence.cohortMembershipId())) {
                        presenceTransitionService.closeMeetingWithoutReturn(
                                presence.attendanceId(),
                                presence.cohortMembershipId(),
                                meetingSpaceId,
                                at.toInstant()
                        );
                    } else {
                        presenceTransitionService.leaveMeeting(
                                presence.attendanceId(),
                                presence.cohortMembershipId(),
                                meetingSpaceId,
                                at.toInstant()
                        );
                    }
                });
    }

    private Map<Long, OpenPresenceView> requireMeetingPresences(
            Collection<Long> membershipIds,
            Long meetingSpaceId
    ) {
        Map<Long, OpenPresenceView> presences = attendancePresenceQueryService
                .findOpenMeetingPresencesByMembershipIds(membershipIds, meetingSpaceId);
        if (!presences.keySet().containsAll(membershipIds)) {
            throw new BusinessException(AttendanceErrorCode.PRESENCE_ACTIVE_INTERVAL_REQUIRED);
        }
        return presences;
    }

    private Map<Long, OpenPresenceView> requireLatestPresences(
            Collection<Long> membershipIds
    ) {
        Map<Long, OpenPresenceView> presences = attendancePresenceQueryService
                .findOpenPresencesByMembershipIds(membershipIds);
        if (!presences.keySet().containsAll(membershipIds)) {
            throw new BusinessException(AttendanceErrorCode.PRESENCE_ACTIVE_INTERVAL_REQUIRED);
        }
        return presences;
    }
}
