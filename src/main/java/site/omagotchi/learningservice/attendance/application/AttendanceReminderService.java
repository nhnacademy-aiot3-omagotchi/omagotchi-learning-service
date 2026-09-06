package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderSender;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminderSchedule;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceReminderRepository;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortAttendancePolicyRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static site.omagotchi.learningservice.attendance.domain.ReminderChannel.TELEGRAM;

/** 입실·퇴실 예정 시각이 가까운 활성 기수원에게 출결 알림을 보낸다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceReminderService {

    static final Duration LEAD_TIME = Duration.ofMinutes(5);
    static final Duration WINDOW = Duration.ofMinutes(30);

    private final CohortAttendancePolicyRepository policyRepository;
    private final CohortMembershipRepository membershipRepository;
    private final CohortRepository cohortRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceReminderRepository reminderRepository;
    private final AttendanceReminderSender sender;
    private final Clock clock;

    /** 모든 기수 정책을 확인하고 현재 30분 발화 창에 들어온 알림만 처리한다. */
    public void sendDue() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate attendanceDate = AggregationDateTime.today(clock);

        for (CohortAttendancePolicy policy : policyRepository.findAll()) {
            try {
                sendDue(policy, attendanceDate, now);
            } catch (Exception exception) {
                log.error(
                        "기수 출결 알림 처리에 실패했습니다. 다른 기수는 계속 처리합니다. cohortId={}",
                        policy.getCohortId(),
                        exception
                );
            }
        }
    }

    private void sendDue(
            CohortAttendancePolicy policy,
            LocalDate attendanceDate,
            OffsetDateTime now
    ) {
        for (ReminderType type : ReminderType.values()) {
            OffsetDateTime fireAt = AttendanceReminderSchedule.fireAt(
                    attendanceDate,
                    type,
                    policy,
                    LEAD_TIME
            );
            if (!isInWindow(now, fireAt)) {
                continue;
            }
            sendDue(policy, attendanceDate, type, fireAt.plus(LEAD_TIME));
        }
    }

    private void sendDue(
            CohortAttendancePolicy policy,
            LocalDate attendanceDate,
            ReminderType type,
            OffsetDateTime scheduledAt
    ) {
        List<CohortMembership> memberships = membershipRepository
                .findByCohortIdAndStatusOrderByRequestedAtAsc(
                        policy.getCohortId(),
                        CohortMembershipStatus.ACTIVE
                );
        if (memberships.isEmpty()) {
            return;
        }

        List<Long> membershipIds = memberships.stream()
                .map(CohortMembership::getId)
                .toList();
        Set<Long> recordedMembershipIds = new HashSet<>(
                reminderRepository.findRecordedMembershipIds(
                        attendanceDate,
                        type,
                        TELEGRAM,
                        membershipIds
                )
        );
        List<CohortMembership> unrecordedMemberships = memberships.stream()
                .filter(membership -> !recordedMembershipIds.contains(membership.getId()))
                .toList();
        if (unrecordedMemberships.isEmpty()) {
            return;
        }

        List<Long> unrecordedMembershipIds = unrecordedMemberships.stream()
                .map(CohortMembership::getId)
                .toList();
        Map<Long, AttendanceRecord> recordsByMembershipId = recordsByMembershipId(
                attendanceRecordRepository.findByAttendanceDateAndCohortMembershipIdIn(
                        attendanceDate,
                        unrecordedMembershipIds
                )
        );

        List<CohortMembership> targets = unrecordedMemberships.stream()
                .filter(membership -> needsReminder(
                        type,
                        recordsByMembershipId.get(membership.getId())
                ))
                .toList();
        if (targets.isEmpty()) {
            return;
        }

        Cohort cohort = cohortRepository.findById(policy.getCohortId())
                .orElseThrow(() -> new IllegalStateException(
                        "출결 알림 기수를 찾을 수 없습니다. cohortId=" + policy.getCohortId()
                ));

        for (CohortMembership membership : targets) {
            if (!record(membership.getId(), attendanceDate, type)) {
                continue;
            }
            sendAsync(
                    new AttendanceReminderSender.Reminder(
                            membership.getUserId(),
                            cohort.getName(),
                            type,
                            scheduledAt
                    ),
                    membership.getId()
            );
        }
    }

    private Map<Long, AttendanceRecord> recordsByMembershipId(List<AttendanceRecord> records) {
        Map<Long, AttendanceRecord> recordsByMembershipId = new HashMap<>();
        for (AttendanceRecord record : records) {
            recordsByMembershipId.put(record.getCohortMembershipId(), record);
        }
        return recordsByMembershipId;
    }

    private boolean isInWindow(OffsetDateTime now, OffsetDateTime fireAt) {
        return !now.isBefore(fireAt) && !now.isAfter(fireAt.plus(WINDOW));
    }

    private boolean needsReminder(ReminderType type, AttendanceRecord record) {
        boolean checkedIn = record != null && record.getCheckedInAt() != null;
        boolean checkedOut = record != null && record.getCheckedOutAt() != null;

        return switch (type) {
            case CHECK_IN_BEFORE_DEADLINE -> !checkedIn;
            case CHECK_OUT_BEFORE_DEADLINE -> checkedIn && !checkedOut;
        };
    }

    private boolean record(Long membershipId, LocalDate attendanceDate, ReminderType type) {
        try {
            // flush까지 끝난 뒤에만 발송한다. 그래야 유니크 제약이 발송보다 먼저 판정된다.
            reminderRepository.saveAndFlush(
                    AttendanceReminder.sent(membershipId, attendanceDate, type, TELEGRAM)
            );
            return true;
        } catch (DataIntegrityViolationException exception) {
            log.debug(
                    "이미 기록된 출결 알림이라 중복 발송하지 않습니다. membershipId={}, "
                            + "attendanceDate={}, type={}",
                    membershipId,
                    attendanceDate,
                    type
            );
            return false;
        }
    }

    private void sendAsync(AttendanceReminderSender.Reminder reminder, Long membershipId) {
        try {
            sender.sendAsync(reminder).whenComplete((sent, exception) -> {
                if (exception == null) {
                    return;
                }
                logDeliveryFailure(membershipId, reminder.type(), exception);
            });
        } catch (Exception exception) {
            logDeliveryFailure(membershipId, reminder.type(), exception);
        }
    }

    private void logDeliveryFailure(
            Long membershipId,
            ReminderType type,
            Throwable exception
    ) {
        Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        log.warn(
                "출결 알림 발송에 실패했습니다. 이력은 유지하고 재시도하지 않습니다. "
                        + "membershipId={}, type={}",
                membershipId,
                type,
                cause
        );
    }
}
