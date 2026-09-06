package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.port.AttendanceRecordQueryRepository;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderPersistence;
import site.omagotchi.learningservice.attendance.application.result.AttendanceReminderAttempt;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.result.CohortAttendancePolicyResponse;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("출결 알림 서비스")
class AttendanceReminderServiceTest {

    private static final Long COHORT_ID = 1L;
    private static final Long MEMBERSHIP_ID = 10L;
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2026, 9, 5);
    private static final Instant CHECK_IN_WINDOW = Instant.parse("2026-09-04T23:55:30Z");
    private static final Instant CHECK_OUT_WINDOW = Instant.parse("2026-09-05T08:55:30Z");
    private static final Instant AFTER_MIDNIGHT_CHECK_IN_WINDOW =
            Instant.parse("2026-09-05T14:58:30Z");

    @Mock
    private CohortAttendancePolicyService policyService;

    @Mock
    private CohortMembershipQueryService membershipQueryService;

    @Mock
    private CohortService cohortService;

    @Mock
    private AttendanceRecordQueryRepository attendanceRecordQueryRepository;

    @Mock
    private AttendanceReminderPersistence reminderPersistence;

    @Mock
    private AttendanceReminderAttemptService reminderAttemptService;

    @Mock
    private TelegramNotificationService telegramNotificationService;

    @Test
    @DisplayName("발화 창 밖에서는 기수원도 조회하지 않는다")
    void skipsQueriesOutsideWindow() {
        given(policyService.findAllPolicies()).willReturn(List.of(policy()));

        serviceAt(Instant.parse("2026-09-04T22:00:00Z")).sendDue();

        verifyNoInteractions(
                membershipQueryService,
                cohortService,
                attendanceRecordQueryRepository,
                reminderPersistence,
                reminderAttemptService,
                telegramNotificationService
        );
    }

    @Test
    @DisplayName("입실 기록이 없는 활성 기수원에게 설정 입실 시각을 알려 준다")
    void sendsCheckInReminderToUncheckedMember() {
        CohortMembershipView membership = membership(MEMBERSHIP_ID, USER_ID);
        prepareDue(ReminderType.CHECK_IN_BEFORE_DEADLINE, membership, List.of());
        given(telegramNotificationService.sendAsync(any(), any()))
                .willReturn(CompletableFuture.completedFuture(true));

        serviceAt(CHECK_IN_WINDOW).sendDue();

        verify(reminderAttemptService).start(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        );
        verify(reminderAttemptService).markSent(attempt(
                MEMBERSHIP_ID,
                ReminderType.CHECK_IN_BEFORE_DEADLINE
        ));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegramNotificationService).sendAsync(eq(USER_ID), message.capture());
        assertThat(message.getValue())
                .contains("[입실 안내]")
                .contains("기수: AIoT 3기")
                .contains("입실 시각: 2026-09-05 09:00:00 (KST)");
    }

    @Test
    @DisplayName("04시 이전 입실 시각은 같은 집계일의 다음 달력 날짜에 알린다")
    void sendsReminderForTimeBeforeAggregationBoundary() {
        CohortMembershipView membership = membership(MEMBERSHIP_ID, USER_ID);
        prepareDue(
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                membership,
                List.of(),
                afterMidnightPolicy()
        );
        given(telegramNotificationService.sendAsync(any(), any()))
                .willReturn(CompletableFuture.completedFuture(true));

        serviceAt(AFTER_MIDNIGHT_CHECK_IN_WINDOW).sendDue();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegramNotificationService).sendAsync(eq(USER_ID), message.capture());
        assertThat(message.getValue())
                .contains("입실 시각: 2026-09-06 00:03:00 (KST)");
    }

    @Test
    @DisplayName("지각 상태여도 입실 타임스탬프가 있으면 입실 알림을 보내지 않는다")
    void skipsCheckInReminderByTimestampRegardlessOfStatus() {
        CohortMembershipView membership = membership(MEMBERSHIP_ID, USER_ID);
        AttendanceRecord record = checkedInRecord();
        given(policyService.findAllPolicies()).willReturn(List.of(policy()));
        given(membershipQueryService.findActiveMemberships(COHORT_ID))
                .willReturn(List.of(membership));
        given(reminderPersistence.findBlockingMembershipIds(
                eq(ATTENDANCE_DATE),
                eq(ReminderType.CHECK_IN_BEFORE_DEADLINE),
                eq(ReminderChannel.TELEGRAM),
                eq(List.of(MEMBERSHIP_ID)),
                any(OffsetDateTime.class)
        )).willReturn(List.of());
        given(attendanceRecordQueryRepository.findDailyRecords(
                ATTENDANCE_DATE,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of(record));

        serviceAt(CHECK_IN_WINDOW).sendDue();

        verifyNoInteractions(cohortService, reminderAttemptService, telegramNotificationService);
    }

    @Test
    @DisplayName("입실했고 퇴실하지 않은 기수원에게만 퇴실 알림을 보낸다")
    void sendsCheckOutReminderOnlyAfterCheckIn() {
        CohortMembershipView checkedIn = membership(MEMBERSHIP_ID, USER_ID);
        CohortMembershipView absent = membership(11L, UUID.randomUUID());
        prepareDue(
                ReminderType.CHECK_OUT_BEFORE_DEADLINE,
                List.of(checkedIn, absent),
                List.of(checkedInRecord())
        );
        given(telegramNotificationService.sendAsync(any(), any()))
                .willReturn(CompletableFuture.completedFuture(true));

        serviceAt(CHECK_OUT_WINDOW).sendDue();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegramNotificationService).sendAsync(eq(USER_ID), message.capture());
        assertThat(message.getValue())
                .contains("[퇴실 체크 안내]")
                .contains("퇴실 체크를 하지 않으면 출결이 확정되지 않습니다.")
                .contains("퇴실 시각: 2026-09-05 18:00:00 (KST)")
                .doesNotContain("마감");
    }

    @Test
    @DisplayName("SENT와 아직 진행 중인 이력이 있는 기수원은 다시 보내지 않는다")
    void skipsBlockingReminder() {
        CohortMembershipView membership = membership(MEMBERSHIP_ID, USER_ID);
        given(policyService.findAllPolicies()).willReturn(List.of(policy()));
        given(membershipQueryService.findActiveMemberships(COHORT_ID))
                .willReturn(List.of(membership));
        given(reminderPersistence.findBlockingMembershipIds(
                eq(ATTENDANCE_DATE),
                eq(ReminderType.CHECK_IN_BEFORE_DEADLINE),
                eq(ReminderChannel.TELEGRAM),
                eq(List.of(MEMBERSHIP_ID)),
                any(OffsetDateTime.class)
        )).willReturn(List.of(MEMBERSHIP_ID));

        serviceAt(CHECK_IN_WINDOW).sendDue();

        verifyNoInteractions(attendanceRecordQueryRepository, cohortService);
        verifyNoInteractions(reminderAttemptService, telegramNotificationService);
    }

    @Test
    @DisplayName("다른 실행이 발송 시도를 먼저 획득했으면 보내지 않는다")
    void concurrentAttemptPreventsDuplicateSend() {
        CohortMembershipView membership = membership(MEMBERSHIP_ID, USER_ID);
        prepareDue(ReminderType.CHECK_IN_BEFORE_DEADLINE, membership, List.of());
        given(reminderAttemptService.start(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        )).willReturn(Optional.empty());

        serviceAt(CHECK_IN_WINDOW).sendDue();

        verifyNoInteractions(telegramNotificationService);
    }

    @Test
    @DisplayName("텔레그램이 false로 완료되면 SENT가 아니라 SKIPPED로 확정한다")
    void marksSkippedWhenTelegramDoesNotSend() {
        CohortMembershipView membership = membership(MEMBERSHIP_ID, USER_ID);
        prepareDue(ReminderType.CHECK_IN_BEFORE_DEADLINE, membership, List.of());
        given(telegramNotificationService.sendAsync(any(), any()))
                .willReturn(CompletableFuture.completedFuture(false));

        serviceAt(CHECK_IN_WINDOW).sendDue();

        AttendanceReminderAttempt attempt = attempt(
                MEMBERSHIP_ID,
                ReminderType.CHECK_IN_BEFORE_DEADLINE
        );
        verify(reminderAttemptService).markSkipped(eq(attempt), any(String.class));
        verify(reminderAttemptService, never()).markSent(any());
        verify(reminderAttemptService, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("한 사람의 텔레그램 응답을 기다리지 않고 다음 사람 발송을 시작한다")
    void dispatchesNextDeliveryWithoutWaitingForPreviousResponse() {
        CohortMembershipView first = membership(MEMBERSHIP_ID, USER_ID);
        CohortMembershipView second = membership(11L, UUID.randomUUID());
        prepareDue(
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                List.of(first, second),
                List.of()
        );
        CompletableFuture<Boolean> delayedDelivery = new CompletableFuture<>();
        given(telegramNotificationService.sendAsync(any(), any()))
                .willReturn(delayedDelivery, CompletableFuture.completedFuture(true));

        assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> serviceAt(CHECK_IN_WINDOW).sendDue()
        );
        delayedDelivery.completeExceptionally(
                new IllegalStateException("telegram unavailable")
        );

        verify(telegramNotificationService, times(2)).sendAsync(any(), any());
        verify(reminderAttemptService).markFailed(
                eq(attempt(MEMBERSHIP_ID, ReminderType.CHECK_IN_BEFORE_DEADLINE)),
                any(String.class)
        );
        verify(reminderAttemptService).markSent(
                attempt(11L, ReminderType.CHECK_IN_BEFORE_DEADLINE)
        );
    }

    @Test
    @DisplayName("손상된 기수 정책이 있어도 다음 기수 알림은 계속 처리한다")
    void isolatesDamagedPolicyPerCohort() {
        CohortAttendancePolicyResponse damaged = policy(
                99L,
                "invalid/timezone",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );
        CohortMembershipView membership = membership(MEMBERSHIP_ID, USER_ID);
        given(policyService.findAllPolicies()).willReturn(List.of(damaged, policy()));
        prepareDueQueries(ReminderType.CHECK_IN_BEFORE_DEADLINE, List.of(membership), List.of());
        given(telegramNotificationService.sendAsync(any(), any()))
                .willReturn(CompletableFuture.completedFuture(true));

        assertThatCode(() -> serviceAt(CHECK_IN_WINDOW).sendDue())
                .doesNotThrowAnyException();

        verify(telegramNotificationService).sendAsync(any(), any());
        verify(membershipQueryService, never()).findActiveMemberships(99L);
    }

    private void prepareDue(
            ReminderType type,
            CohortMembershipView membership,
            List<AttendanceRecord> records
    ) {
        prepareDue(type, List.of(membership), records);
    }

    private void prepareDue(
            ReminderType type,
            CohortMembershipView membership,
            List<AttendanceRecord> records,
            CohortAttendancePolicyResponse attendancePolicy
    ) {
        prepareDue(type, List.of(membership), records, attendancePolicy);
    }

    private void prepareDue(
            ReminderType type,
            List<CohortMembershipView> memberships,
            List<AttendanceRecord> records
    ) {
        prepareDue(type, memberships, records, policy());
    }

    private void prepareDue(
            ReminderType type,
            List<CohortMembershipView> memberships,
            List<AttendanceRecord> records,
            CohortAttendancePolicyResponse attendancePolicy
    ) {
        given(policyService.findAllPolicies()).willReturn(List.of(attendancePolicy));
        prepareDueQueries(type, memberships, records);
    }

    private void prepareDueQueries(
            ReminderType type,
            List<CohortMembershipView> memberships,
            List<AttendanceRecord> records
    ) {
        List<Long> membershipIds = memberships.stream()
                .map(CohortMembershipView::membershipId)
                .toList();
        given(membershipQueryService.findActiveMemberships(COHORT_ID))
                .willReturn(memberships);
        given(reminderPersistence.findBlockingMembershipIds(
                eq(ATTENDANCE_DATE),
                eq(type),
                eq(ReminderChannel.TELEGRAM),
                eq(membershipIds),
                any(OffsetDateTime.class)
        )).willReturn(List.of());
        given(attendanceRecordQueryRepository.findDailyRecords(
                ATTENDANCE_DATE,
                membershipIds
        )).willReturn(records);
        given(cohortService.getCohortName(COHORT_ID)).willReturn("AIoT 3기");
        given(reminderAttemptService.start(
                anyLong(),
                eq(ATTENDANCE_DATE),
                eq(type),
                eq(ReminderChannel.TELEGRAM)
        )).willAnswer(invocation -> Optional.of(attempt(
                invocation.getArgument(0),
                type
        )));
    }

    private AttendanceReminderService serviceAt(Instant instant) {
        return new AttendanceReminderService(
                policyService,
                membershipQueryService,
                cohortService,
                attendanceRecordQueryRepository,
                reminderPersistence,
                reminderAttemptService,
                telegramNotificationService,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private CohortAttendancePolicyResponse policy() {
        return policy(
                COHORT_ID,
                "Asia/Seoul",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
        );
    }

    private CohortAttendancePolicyResponse afterMidnightPolicy() {
        return policy(
                COHORT_ID,
                "Asia/Seoul",
                LocalTime.of(0, 3),
                LocalTime.of(3, 0)
        );
    }

    private CohortAttendancePolicyResponse policy(
            Long cohortId,
            String timezone,
            LocalTime startTime,
            LocalTime endTime
    ) {
        return new CohortAttendancePolicyResponse(
                cohortId,
                timezone,
                startTime,
                endTime,
                null,
                30,
                ADMIN_ID,
                null
        );
    }

    private CohortMembershipView membership(Long membershipId, UUID userId) {
        return new CohortMembershipView(membershipId, COHORT_ID, userId);
    }

    private AttendanceReminderAttempt attempt(Long membershipId, ReminderType type) {
        return new AttendanceReminderAttempt(
                membershipId,
                ATTENDANCE_DATE,
                type,
                ReminderChannel.TELEGRAM,
                1
        );
    }

    private AttendanceRecord checkedInRecord() {
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        record.checkIn(Instant.parse("2026-09-05T00:01:00Z"), AttendanceStatus.LATE, 1);
        return record;
    }
}
