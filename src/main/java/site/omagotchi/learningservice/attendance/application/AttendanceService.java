package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.command.ChangeAttendanceStatusCommand;
import site.omagotchi.learningservice.attendance.application.event.AttendanceCheckedInEvent;
import site.omagotchi.learningservice.attendance.application.port.AttendanceEventPublisher;
import site.omagotchi.learningservice.attendance.application.port.AttendanceActiveMeetingPort;
import site.omagotchi.learningservice.attendance.application.port.AttendanceLabAccessPort;
import site.omagotchi.learningservice.attendance.application.port.AttendanceRecordQueryRepository;
import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordResult;
import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordPageResult;
import site.omagotchi.learningservice.attendance.application.query.AttendancePageQuery;
import site.omagotchi.learningservice.attendance.domain.AttendanceChangeLog;
import site.omagotchi.learningservice.attendance.domain.AttendanceDecision;
import site.omagotchi.learningservice.attendance.domain.AttendanceDecisionPolicy;
import site.omagotchi.learningservice.attendance.application.AttendanceErrorCode;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.domain.PresenceInterval;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceChangeLogRepository;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.attendance.infrastructure.PresenceIntervalRepository;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
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
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final CohortMembershipRepository membershipRepository;
    private final CohortAttendancePolicyRepository attendancePolicyRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceRecordQueryRepository attendanceRecordQueryRepository;
    private final AttendanceChangeLogRepository attendanceChangeLogRepository;
    private final PresenceIntervalRepository presenceIntervalRepository;
    private final PresenceTransitionService presenceTransitionService;
    private final AttendanceLabAccessPort attendanceLabAccessPort;
    private final AttendanceActiveMeetingPort attendanceActiveMeetingPort;
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
                .findByCohortMembershipIdAndAttendanceDate(membership.getId(), attendanceDate)
                .orElseGet(() -> AttendanceRecord.start(membership.getId(), attendanceDate));

        if (record.getCheckedInAt() != null) {
            return AttendanceRecordResult.from(record);
        }

        AttendanceDecision decision = AttendanceDecisionPolicy.decideCheckIn(policy, now);
        record.checkIn(now, decision.status(), decision.lateMinutes());

        AttendanceRecord savedRecord = attendanceRecordRepository.save(record);
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
        if (attendanceActiveMeetingPort.existsActiveParticipation(membership.getId())) {
            throw new BusinessException(AttendanceErrorCode.ATTENDANCE_ACTIVE_MEETING_EXISTS);
        }

        CohortAttendancePolicy policy = requirePolicy(cohortId);
        presenceTransitionService.closeAttendance(record.getId(), now);
        List<PresenceInterval> intervals = presenceIntervalRepository.findByAttendanceIdOrderByStartedAtAsc(record.getId());
        AttendanceDecision decision = AttendanceDecisionPolicy.decide(
                policy,
                record.getCheckedInAt(),
                now,
                intervals
        );
        record.checkOut(now, decision.status(), decision.earlyLeaveMinutes());
        record.applyDecision(decision);

        return AttendanceRecordResult.from(attendanceRecordRepository.save(record));
    }

    /** 체크인 후 최초 LAB을 선택하거나 현재 LAB에서 자기 기수의 다른 활성 LAB으로 옮긴다. */
    @Transactional
    public AttendanceRecordResult moveLab(Long cohortId, UUID userId, Long spaceId) {
        CohortMembership membership = cohortAccessService.requireActiveMembership(cohortId, userId);
        lockActiveMembership(membership.getId());
        Instant now = clock.instant();
        LocalDate attendanceDate = AggregationDateTime.aggregationDate(now);

        AttendanceRecord record = attendanceRecordRepository
                .findByCohortMembershipIdAndAttendanceDate(membership.getId(), attendanceDate)
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.ATTENDANCE_RECORD_NOT_FOUND));
        if (record.getCheckedInAt() == null) {
            throw new BusinessException(AttendanceErrorCode.ATTENDANCE_CHECK_IN_REQUIRED);
        }
        if (record.getCheckedOutAt() != null) {
            throw new BusinessException(AttendanceErrorCode.PRESENCE_TRANSITION_NOT_ALLOWED);
        }
        if (attendanceActiveMeetingPort.existsActiveParticipation(membership.getId())) {
            throw new BusinessException(
                    AttendanceErrorCode.PRESENCE_MEETING_EXIT_REQUIRED
            );
        }

        attendanceLabAccessPort.requireSelectableLab(cohortId, record.getId(), spaceId);
        presenceTransitionService.moveLab(record.getId(), membership.getId(), spaceId, now);
        return AttendanceRecordResult.from(record);
    }

    public AttendanceRecordPageResult getMyRecords(
            Long cohortId,
            UUID userId,
            AttendancePageQuery query
    ) {
        Long membershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);
        return pageResult(attendanceRecordQueryRepository.findMemberRecords(
                membershipId,
                query.from(),
                query.to(),
                query.page(),
                query.size()
        ));
    }

    public AttendanceRecordPageResult getDailyRecords(
            Long cohortId,
            UUID managerUserId,
            LocalDate date,
            AttendancePageQuery query
    ) {
        cohortAccessService.requireManager(cohortId, managerUserId);

        List<Long> membershipIds = membershipRepository
                .findByCohortIdAndStatusOrderByRequestedAtAsc(cohortId, CohortMembershipStatus.ACTIVE)
                .stream()
                .map(CohortMembership::getId)
                .toList();

        return pageResult(attendanceRecordQueryRepository.findDailyRecords(
                date,
                membershipIds,
                query.page(),
                query.size()
        ));
    }

    private AttendanceRecordPageResult pageResult(
            AttendanceRecordQueryRepository.AttendanceRecordPage records
    ) {
        return new AttendanceRecordPageResult(
                records.items().stream().map(AttendanceRecordResult::from).toList(),
                records.page(),
                records.size(),
                records.totalElements(),
                records.totalPages()
        );
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
        Long recordCohortId = cohortMembershipQueryService
                .findCohortIds(List.of(record.getCohortMembershipId()))
                .get(record.getCohortMembershipId());
        if (!cohortId.equals(recordCohortId)) {
            throw new BusinessException(AttendanceErrorCode.ATTENDANCE_RECORD_NOT_FOUND);
        }
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
