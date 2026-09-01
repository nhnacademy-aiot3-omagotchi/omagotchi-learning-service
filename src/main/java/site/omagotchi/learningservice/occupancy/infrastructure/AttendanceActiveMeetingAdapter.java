package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.attendance.application.port.AttendanceActiveMeetingPort;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;

/** 점유 기능의 활성 참여 사실을 출결 체크아웃 가드에 제공한다. */
@Component
@RequiredArgsConstructor
public class AttendanceActiveMeetingAdapter implements AttendanceActiveMeetingPort {

    private final OccupancyParticipantRepository participantRepository;

    @Override
    public boolean existsActiveParticipation(Long cohortMembershipId) {
        return participantRepository.existsActiveParticipationByCohortMembershipId(
                cohortMembershipId
        );
    }
}
