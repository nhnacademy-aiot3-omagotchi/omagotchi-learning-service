package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.attendance.application.port.AttendanceRecordQueryRepository;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderPersistence;
import site.omagotchi.learningservice.attendance.application.result.AttendanceReminderAttempt;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminderSchedule;
import site.omagotchi.learningservice.attendance.domain.ReminderType;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.result.CohortAttendancePolicyResponse;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.global.util.DateTimePolicy;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static site.omagotchi.learningservice.attendance.domain.ReminderChannel.TELEGRAM;

/** 입실·퇴실 예정 시각이 가까운 활성 기수원에게 출결 알림을 보낸다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceReminderService {

    static final Duration LEAD_TIME = Duration.ofMinutes(5);
    static final Duration WINDOW = Duration.ofMinutes(30);
    private static final int LAST_ERROR_MAX_LENGTH = 500;
    private static final String SKIPPED_REASON =
            "텔레그램 미연동 또는 알림 비활성화로 발송을 건너뛰었습니다.";
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '('zzz')'", Locale.KOREA);

    private final CohortAttendancePolicyService policyService;
    private final CohortMembershipQueryService membershipQueryService;
    private final CohortService cohortService;
    private final AttendanceRecordQueryRepository attendanceRecordQueryRepository;
    private final AttendanceReminderPersistence reminderPersistence;
    private final AttendanceReminderAttemptService reminderAttemptService;
    private final TelegramNotificationService telegramNotificationService;
    private final Clock clock;

    /** 모든 기수 정책을 확인하고 현재 30분 발화 창에 들어온 알림만 처리한다. */
    public void sendDue() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate attendanceDate = AggregationDateTime.today(clock);

        for (CohortAttendancePolicyResponse policy : policyService.findAllPolicies()) {
            try {
                sendDue(policy, attendanceDate, now);
            } catch (Exception exception) {
                log.error(
                        "기수 출결 알림 처리에 실패했습니다. 다른 기수는 계속 처리합니다. cohortId={}",
                        policy.cohortId(),
                        exception
                );
            }
        }
    }

    private void sendDue(
            CohortAttendancePolicyResponse policy,
            LocalDate attendanceDate,
            OffsetDateTime now
    ) {
        for (ReminderType type : ReminderType.values()) {
            OffsetDateTime scheduledAt = scheduledAt(attendanceDate, type, policy);
            OffsetDateTime fireAt = AttendanceReminderSchedule.fireAt(
                    scheduledAt,
                    LEAD_TIME
            );
            if (!isInWindow(now, fireAt)) {
                continue;
            }
            sendDue(policy.cohortId(), attendanceDate, type, scheduledAt, now);
        }
    }

    private void sendDue(
            Long cohortId,
            LocalDate attendanceDate,
            ReminderType type,
            OffsetDateTime scheduledAt,
            OffsetDateTime now
    ) {
        List<CohortMembershipView> memberships =
                membershipQueryService.findActiveMemberships(cohortId);
        if (memberships.isEmpty()) {
            return;
        }

        List<Long> membershipIds = memberships.stream()
                .map(CohortMembershipView::membershipId)
                .toList();
        Set<Long> blockingMembershipIds = new HashSet<>(
                reminderPersistence.findBlockingMembershipIds(
                        attendanceDate,
                        type,
                        TELEGRAM,
                        membershipIds,
                        now.minus(AttendanceReminderAttemptService.PENDING_TIMEOUT)
                )
        );
        List<CohortMembershipView> candidateMemberships = memberships.stream()
                .filter(membership -> !blockingMembershipIds.contains(membership.membershipId()))
                .toList();
        if (candidateMemberships.isEmpty()) {
            return;
        }

        List<Long> candidateMembershipIds = candidateMemberships.stream()
                .map(CohortMembershipView::membershipId)
                .toList();
        Map<Long, AttendanceRecord> recordsByMembershipId = recordsByMembershipId(
                attendanceRecordQueryRepository.findDailyRecords(
                        attendanceDate,
                        candidateMembershipIds
                )
        );

        List<CohortMembershipView> targets = candidateMemberships.stream()
                .filter(membership -> needsReminder(
                        type,
                        recordsByMembershipId.get(membership.membershipId())
                ))
                .toList();
        if (targets.isEmpty()) {
            return;
        }

        String cohortName = cohortService.getCohortName(cohortId);

        for (CohortMembershipView membership : targets) {
            var attempt = reminderAttemptService.start(
                    membership.membershipId(),
                    attendanceDate,
                    type,
                    TELEGRAM
            );
            if (attempt.isEmpty()) {
                continue;
            }
            sendAsync(
                    membership.userId(),
                    cohortName,
                    type,
                    scheduledAt,
                    attempt.get()
            );
        }
    }

    private OffsetDateTime scheduledAt(
            LocalDate attendanceDate,
            ReminderType type,
            CohortAttendancePolicyResponse policy
    ) {
        LocalTime scheduledTime = switch (type) {
            case CHECK_IN_BEFORE_DEADLINE -> policy.scheduledStartTime();
            case CHECK_OUT_BEFORE_DEADLINE -> policy.scheduledEndTime();
        };
        return AggregationDateTime.dateTimeWithin(
                        attendanceDate,
                        scheduledTime,
                        ZoneId.of(policy.timezone())
                )
                .toOffsetDateTime();
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

    private void sendAsync(
            UUID recipientUserId,
            String cohortName,
            ReminderType type,
            OffsetDateTime scheduledAt,
            AttendanceReminderAttempt attempt
    ) {
        try {
            telegramNotificationService.sendAsync(
                    recipientUserId,
                    messageOf(cohortName, type, scheduledAt)
            ).whenComplete((sent, exception) -> {
                if (exception != null) {
                    recordFailure(attempt, exception);
                    return;
                }
                if (Boolean.TRUE.equals(sent)) {
                    markSent(attempt);
                    return;
                }
                markSkipped(attempt);
            });
        } catch (Exception exception) {
            recordFailure(attempt, exception);
        }
    }

    private void markSent(AttendanceReminderAttempt attempt) {
        try {
            reminderAttemptService.markSent(attempt);
        } catch (Exception exception) {
            log.error(
                    "출결 알림 성공 상태를 기록하지 못했습니다. membershipId={}, type={}",
                    attempt.cohortMembershipId(),
                    attempt.reminderType(),
                    exception
            );
        }
    }

    private void markSkipped(AttendanceReminderAttempt attempt) {
        try {
            reminderAttemptService.markSkipped(attempt, SKIPPED_REASON);
        } catch (Exception exception) {
            log.error(
                    "출결 알림 건너뜀 상태를 기록하지 못했습니다. membershipId={}, type={}",
                    attempt.cohortMembershipId(),
                    attempt.reminderType(),
                    exception
            );
        }
    }

    private void recordFailure(AttendanceReminderAttempt attempt, Throwable exception) {
        Throwable cause = unwrap(exception);
        try {
            reminderAttemptService.markFailed(attempt, failureMessage(cause));
        } catch (Exception stateException) {
            log.error(
                    "출결 알림 실패 상태를 기록하지 못했습니다. membershipId={}, type={}",
                    attempt.cohortMembershipId(),
                    attempt.reminderType(),
                    stateException
            );
        }
        logDeliveryFailure(attempt.cohortMembershipId(), attempt.reminderType(), cause);
    }

    private String failureMessage(Throwable cause) {
        String message = cause.toString();
        return message.length() <= LAST_ERROR_MAX_LENGTH
                ? message
                : message.substring(0, LAST_ERROR_MAX_LENGTH);
    }

    private Throwable unwrap(Throwable exception) {
        return exception instanceof CompletionException && exception.getCause() != null
                ? exception.getCause()
                : exception;
    }

    private static String messageOf(
            String cohortName,
            ReminderType type,
            OffsetDateTime scheduledAt
    ) {
        String displayedScheduledAt = scheduledAt
                .atZoneSameInstant(DateTimePolicy.ZONE_ID)
                .format(DISPLAY_FORMATTER);

        return switch (type) {
            case CHECK_IN_BEFORE_DEADLINE -> """
                    [입실 안내]

                    기수: %s
                    입실 시각까지 얼마 남지 않았습니다.
                    입실 시각: %s
                    """.formatted(cohortName, displayedScheduledAt).stripTrailing();
            case CHECK_OUT_BEFORE_DEADLINE -> """
                    [퇴실 체크 안내]

                    기수: %s
                    퇴실 시각까지 얼마 남지 않았습니다.
                    퇴실 체크를 하지 않으면 출결이 확정되지 않습니다.
                    퇴실 시각: %s
                    """.formatted(cohortName, displayedScheduledAt).stripTrailing();
        };
    }

    private void logDeliveryFailure(
            Long membershipId,
            ReminderType type,
            Throwable exception
    ) {
        log.warn(
                "출결 알림 발송에 실패했습니다. 다음 주기에 재시도합니다. "
                        + "membershipId={}, type={}",
                membershipId,
                type,
                exception
        );
    }
}
