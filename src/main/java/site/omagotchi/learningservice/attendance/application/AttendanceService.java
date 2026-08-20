package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.command.ChangeAttendanceStatusCommand;
import site.omagotchi.learningservice.attendance.application.event.AttendanceCheckedInEvent;
import site.omagotchi.learningservice.attendance.application.port.AttendanceEventPublisher;
import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordResult;
import site.omagotchi.learningservice.attendance.domain.AttendanceChangeLog;
import site.omagotchi.learningservice.attendance.domain.AttendanceDecision;
import site.omagotchi.learningservice.attendance.domain.AttendanceDecisionPolicy;
import site.omagotchi.learningservice.attendance.domain.AttendanceErrorCode;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.domain.PresenceInterval;
import site.omagotchi.learningservice.attendance.domain.PresenceState;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceChangeLogRepository;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.attendance.infrastructure.PresenceIntervalRepository;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortAttendancePolicyRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 출석 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final CohortAccessService cohortAccessService;
    private final CohortMembershipRepository membershipRepository;
    private final CohortAttendancePolicyRepository attendancePolicyRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceChangeLogRepository attendanceChangeLogRepository;
    private final PresenceIntervalRepository presenceIntervalRepository;
    private final AttendanceEventPublisher attendanceEventPublisher;
    private final Clock clock;

    // 출석 기록 결과 -> 출석(기수 Id, 유저 Id)
    @Transactional
    public AttendanceRecordResult checkIn(Long cohortId, UUID userId) {
        CohortMembership membership = cohortAccessService.requireActiveMembership(cohortId, userId);
        lockActiveMembership(membership.getId());
        CohortAttendancePolicy policy = requirePolicy(cohortId);
        Instant now = clock.instant();
        LocalDate attendanceDate = AggregationDateTime.aggregationDate(now);

        AttendanceRecord record = attendanceRecordRepository
                .findWithLockByCohortMembershipIdAndAttendanceDate(membership.getId(), attendanceDate)
                .orElseGet(() -> AttendanceRecord.start(membership.getId(), attendanceDate));

        if (record.getCheckedInAt() != null) {
            return AttendanceRecordResult.from(record);
        }
        AttendanceDecision decision = AttendanceDecisionPolicy.decideCheckIn(policy, now);
        record.checkIn(now, decision.status(), decision.lateMinutes());

        AttendanceRecord savedRecord = attendanceRecordRepository.save(record);
        presenceIntervalRepository.save(PresenceInterval.start(
                savedRecord.getId(),
                PresenceState.PRESENT,
                null,
                now
        ));
        attendanceEventPublisher.publishCheckedIn(new AttendanceCheckedInEvent(
                userId,
                cohortId,
                savedRecord.getId(),
                now
        ));

        return AttendanceRecordResult.from(savedRecord);
    }

    @Transactional
    public AttendanceRecordResult checkOut(Long cohortId, UUID userId) {
        CohortMembership membership = cohortAccessService.requireActiveMembership(cohortId, userId);
        lockActiveMembership(membership.getId());
        Instant now = clock.instant();
        LocalDate attendanceDate = AggregationDateTime.aggregationDate(now);

        AttendanceRecord record = attendanceRecordRepository
                .findWithLockByCohortMembershipIdAndAttendanceDate(membership.getId(), attendanceDate)
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.ATTENDANCE_RECORD_NOT_FOUND));

        if (record.getCheckedInAt() == null) {
            throw new BusinessException(AttendanceErrorCode.ATTENDANCE_CHECK_IN_REQUIRED);
        }
        if (record.getCheckedOutAt() != null) {
            return AttendanceRecordResult.from(record);
        }

        CohortAttendancePolicy policy = requirePolicy(cohortId);
        List<PresenceInterval> intervals = presenceIntervalRepository.findByAttendanceIdOrderByStartedAtAsc(record.getId());
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy,
                record.getCheckedInAt(),
                now,
                intervals
        );
        record.checkOut(now, decision.status(), decision.earlyLeaveMinutes());
        record.applyDecision(decision);
        presenceIntervalRepository.findFirstByAttendanceIdAndEndedAtIsNullOrderByStartedAtDesc(record.getId())
                .ifPresent(interval -> interval.end(now));

        return AttendanceRecordResult.from(attendanceRecordRepository.save(record));
    }

    public List<AttendanceRecordResult> getMyRecords(Long cohortId, UUID userId) {
        Long membershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        return attendanceRecordRepository.findByCohortMembershipIdOrderByAttendanceDateDesc(membershipId).stream()
                .map(AttendanceRecordResult::from)
                .toList();
    }

    public List<AttendanceRecordResult> getDailyRecords(Long cohortId, UUID managerUserId, java.time.LocalDate date) {
        cohortAccessService.requireManager(cohortId, managerUserId);

        List<Long> membershipIds = membershipRepository
                .findByCohortIdAndStatusOrderByRequestedAtAsc(cohortId, CohortMembershipStatus.ACTIVE)
                .stream()
                .map(CohortMembership::getId)
                .toList();

        if (membershipIds.isEmpty()) {
            return List.of();
        }

        return attendanceRecordRepository
                .findByAttendanceDateAndCohortMembershipIdInOrderByCohortMembershipIdAsc(date, membershipIds)
                .stream()
                .map(AttendanceRecordResult::from)
                .toList();
    }

    @Transactional
    public AttendanceRecordResult changeFinalStatus(
            Long cohortId,
            Long attendanceId,
            UUID managerUserId,
            ChangeAttendanceStatusCommand command
    ) {
        cohortAccessService.requireManager(cohortId, managerUserId);
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException(AttendanceErrorCode.ATTENDANCE_CHANGE_REASON_REQUIRED);
        }

        AttendanceRecord record = attendanceRecordRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.ATTENDANCE_RECORD_NOT_FOUND));
        AttendanceStatus previousStatus = record.getFinalStatus();

        record.overrideFinalStatus(command.nextStatus());
        AttendanceChangeLog log = AttendanceChangeLog.create(
                attendanceId,
                managerUserId,
                previousStatus,
                command.nextStatus(),
                command.reason(),
                command.requestId()
        );
        attendanceChangeLogRepository.save(log);

        return AttendanceRecordResult.from(attendanceRecordRepository.save(record));
    }

    private CohortAttendancePolicy requirePolicy(Long cohortId) {
        return attendancePolicyRepository.findById(cohortId)
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND));
    }

    private void lockActiveMembership(Long membershipId) {
        membershipRepository.findWithLockByIdAndStatus(membershipId, CohortMembershipStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND));
    }
}
