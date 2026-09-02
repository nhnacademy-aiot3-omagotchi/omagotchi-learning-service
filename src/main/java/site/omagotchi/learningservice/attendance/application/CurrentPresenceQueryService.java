package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.port.AttendancePresenceQuery;
import site.omagotchi.learningservice.attendance.application.result.CurrentPresenceResult;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** 공간 화면에 표시할 사용자의 현재 체류구간을 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentPresenceQueryService {

    private final CohortAccessService cohortAccessService;
    private final AttendancePresenceQuery attendancePresenceQuery;
    private final Clock clock;

    public Optional<CurrentPresenceResult> findCurrentPresence(Long cohortId, UUID userId) {
        Long membershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);
        return attendancePresenceQuery.findCurrentPresences(
                        membershipId,
                        AggregationDateTime.today(clock)
                )
                .stream()
                .findFirst();
    }
}
