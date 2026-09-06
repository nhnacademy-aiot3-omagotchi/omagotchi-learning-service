package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.port.AttendanceRecordQueryRepository;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderPersistence;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
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

        ArgumentCaptor<AttendanceReminder> history =
                ArgumentCaptor.forClass(AttendanceReminder.class);
        verify(reminderPersistence).saveIfAbsent(history.capture());
        assertThat(history.getValue().getCohortMembershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(history.getValue().getAttendanceDate()).isEqualTo(ATTENDANCE_DATE);
        assertThat(history.getValue().getReminderType())
                .isEqualTo(ReminderType.CHECK_IN_BEFORE_DEADLINE);

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

        ArgumentCaptor<AttendanceReminder> history =
                ArgumentCaptor.forClass(AttendanceReminder.class);
        verify(reminderPersistence).saveIfAbsent(history.capture());
        assertThat(history.getValue().getAttendanceDate()).isEqualTo(ATTENDANCE_DATE);

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
        given(reminderPersistence.findRecordedMembershipIds(
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of());
        given(attendanceRecordQueryRepository.findDailyRecords(
                ATTENDANCE_DATE,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of(record));

        serviceAt(CHECK_IN_WINDOW).sendDue();

        verifyNoInteractions(cohortService, telegramNotificationService);
        verify(reminderPersistence, never()).saveIfAbsent(any());
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
    @DisplayName("이미 이력이 있는 기수원은 다시 보내지 않는다")
    void skipsAlreadyRecordedReminder() {
        CohortMembershipView membership = membership(MEMBERSHIP_ID, USER_ID);
        given(policyService.findAllPolicies()).willReturn(List.of(policy()));
        given(membershipQueryService.findActiveMemberships(COHORT_ID))
                .willReturn(List.of(membership));
        given(reminderPersistence.findRecordedMembershipIds(
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of(MEMBERSHIP_ID));

        serviceAt(CHECK_IN_WINDOW).sendDue();

        verifyNoInteractions(attendanceRecordQueryRepository, cohortService);
        verify(reminderPersistence, never()).saveIfAbsent(any());
        verifyNoInteractions(telegramNotificationService);
    }

    @Test
    @DisplayName("동시 실행이 이력을 먼저 만들었으면 보내지 않는다")
    void concurrentRecordPreventsDuplicateSend() {
        CohortMembershipView membership = membership(MEMBERSHIP_ID, USER_ID);
        prepareDue(ReminderType.CHECK_IN_BEFORE_DEADLINE, membership, List.of());
        given(reminderPersistence.saveIfAbsent(any())).willReturn(false);

        serviceAt(CHECK_IN_WINDOW).sendDue();

        verifyNoInteractions(telegramNotificationService);
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
        verify(reminderPersistence, times(2)).saveIfAbsent(any());
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
        given(membershipQueryService.findActiveMemberships(COHORT_ID))
                .willReturn(List.of(membership));
        given(reminderPersistence.findRecordedMembershipIds(
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of());
        given(attendanceRecordQueryRepository.findDailyRecords(
                ATTENDANCE_DATE,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of());
        given(cohortService.getCohortName(COHORT_ID)).willReturn("AIoT 3기");
        given(reminderPersistence.saveIfAbsent(any())).willReturn(true);
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
        List<Long> membershipIds = memberships.stream()
                .map(CohortMembershipView::membershipId)
                .toList();
        given(policyService.findAllPolicies()).willReturn(List.of(attendancePolicy));
        given(membershipQueryService.findActiveMemberships(COHORT_ID))
                .willReturn(memberships);
        given(reminderPersistence.findRecordedMembershipIds(
                ATTENDANCE_DATE,
                type,
                ReminderChannel.TELEGRAM,
                membershipIds
        )).willReturn(List.of());
        given(attendanceRecordQueryRepository.findDailyRecords(
                ATTENDANCE_DATE,
                membershipIds
        )).willReturn(records);
        given(cohortService.getCohortName(COHORT_ID)).willReturn("AIoT 3기");
        given(reminderPersistence.saveIfAbsent(any())).willReturn(true);
    }

    private AttendanceReminderService serviceAt(Instant instant) {
        return new AttendanceReminderService(
                policyService,
                membershipQueryService,
                cohortService,
                attendanceRecordQueryRepository,
                reminderPersistence,
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

    private AttendanceRecord checkedInRecord() {
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        record.checkIn(Instant.parse("2026-09-05T00:01:00Z"), AttendanceStatus.LATE, 1);
        return record;
    }
}
