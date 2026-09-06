package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderSender;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
    private CohortAttendancePolicyRepository policyRepository;

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private AttendanceReminderRepository reminderRepository;

    @Mock
    private AttendanceReminderSender sender;

    @Test
    @DisplayName("발화 창 밖에서는 기수원도 조회하지 않는다")
    void skipsQueriesOutsideWindow() {
        given(policyRepository.findAll()).willReturn(List.of(policy()));

        serviceAt(Instant.parse("2026-09-04T22:00:00Z")).sendDue();

        verifyNoInteractions(
                membershipRepository,
                cohortRepository,
                attendanceRecordRepository,
                reminderRepository,
                sender
        );
    }

    @Test
    @DisplayName("입실 기록이 없는 활성 기수원에게 설정 입실 시각을 알려 준다")
    void sendsCheckInReminderToUncheckedMember() {
        CohortMembership membership = membership(MEMBERSHIP_ID);
        given(membership.getUserId()).willReturn(USER_ID);
        prepareDue(ReminderType.CHECK_IN_BEFORE_DEADLINE, membership, List.of());
        given(sender.sendAsync(any())).willReturn(CompletableFuture.completedFuture(true));

        serviceAt(CHECK_IN_WINDOW).sendDue();

        ArgumentCaptor<AttendanceReminder> history =
                ArgumentCaptor.forClass(AttendanceReminder.class);
        verify(reminderRepository).saveAndFlush(history.capture());
        assertThat(history.getValue().getCohortMembershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(history.getValue().getAttendanceDate()).isEqualTo(ATTENDANCE_DATE);
        assertThat(history.getValue().getReminderType())
                .isEqualTo(ReminderType.CHECK_IN_BEFORE_DEADLINE);

        ArgumentCaptor<AttendanceReminderSender.Reminder> message =
                ArgumentCaptor.forClass(AttendanceReminderSender.Reminder.class);
        verify(sender).sendAsync(message.capture());
        assertThat(message.getValue().recipientUserId()).isEqualTo(USER_ID);
        assertThat(message.getValue().cohortName()).isEqualTo("AIoT 3기");
        assertThat(message.getValue().scheduledAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-05T09:00:00+09:00"));
    }

    @Test
    @DisplayName("04시 이전 입실 시각은 같은 집계일의 다음 달력 날짜에 알린다")
    void sendsReminderForTimeBeforeAggregationBoundary() {
        CohortMembership membership = membership(MEMBERSHIP_ID);
        given(membership.getUserId()).willReturn(USER_ID);
        prepareDue(
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                membership,
                List.of(),
                afterMidnightPolicy()
        );
        given(sender.sendAsync(any())).willReturn(CompletableFuture.completedFuture(true));

        serviceAt(AFTER_MIDNIGHT_CHECK_IN_WINDOW).sendDue();

        ArgumentCaptor<AttendanceReminder> history =
                ArgumentCaptor.forClass(AttendanceReminder.class);
        verify(reminderRepository).saveAndFlush(history.capture());
        assertThat(history.getValue().getAttendanceDate()).isEqualTo(ATTENDANCE_DATE);

        ArgumentCaptor<AttendanceReminderSender.Reminder> message =
                ArgumentCaptor.forClass(AttendanceReminderSender.Reminder.class);
        verify(sender).sendAsync(message.capture());
        assertThat(message.getValue().scheduledAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-06T00:03:00+09:00"));
    }

    @Test
    @DisplayName("지각 상태여도 입실 타임스탬프가 있으면 입실 알림을 보내지 않는다")
    void skipsCheckInReminderByTimestampRegardlessOfStatus() {
        CohortMembership membership = membership(MEMBERSHIP_ID);
        AttendanceRecord record = checkedInRecord();
        given(policyRepository.findAll()).willReturn(List.of(policy()));
        given(membershipRepository.findByCohortIdAndStatusOrderByRequestedAtAsc(
                COHORT_ID,
                CohortMembershipStatus.ACTIVE
        )).willReturn(List.of(membership));
        given(reminderRepository.findRecordedMembershipIds(
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of());
        given(attendanceRecordRepository.findByAttendanceDateAndCohortMembershipIdIn(
                ATTENDANCE_DATE,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of(record));

        serviceAt(CHECK_IN_WINDOW).sendDue();

        verify(cohortRepository, never()).findById(any());
        verify(reminderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("입실했고 퇴실하지 않은 기수원에게만 퇴실 알림을 보낸다")
    void sendsCheckOutReminderOnlyAfterCheckIn() {
        CohortMembership checkedIn = membership(MEMBERSHIP_ID);
        CohortMembership absent = membership(11L);
        given(checkedIn.getUserId()).willReturn(USER_ID);
        prepareDue(
                ReminderType.CHECK_OUT_BEFORE_DEADLINE,
                List.of(checkedIn, absent),
                List.of(checkedInRecord())
        );
        given(sender.sendAsync(any())).willReturn(CompletableFuture.completedFuture(true));

        serviceAt(CHECK_OUT_WINDOW).sendDue();

        ArgumentCaptor<AttendanceReminderSender.Reminder> message =
                ArgumentCaptor.forClass(AttendanceReminderSender.Reminder.class);
        verify(sender).sendAsync(message.capture());
        assertThat(message.getValue().recipientUserId()).isEqualTo(USER_ID);
        assertThat(message.getValue().type())
                .isEqualTo(ReminderType.CHECK_OUT_BEFORE_DEADLINE);
        assertThat(message.getValue().scheduledAt())
                .isEqualTo(OffsetDateTime.parse("2026-09-05T18:00:00+09:00"));
    }

    @Test
    @DisplayName("이미 이력이 있는 기수원은 다시 보내지 않는다")
    void skipsAlreadyRecordedReminder() {
        CohortMembership membership = membership(MEMBERSHIP_ID);
        given(policyRepository.findAll()).willReturn(List.of(policy()));
        given(membershipRepository.findByCohortIdAndStatusOrderByRequestedAtAsc(
                COHORT_ID,
                CohortMembershipStatus.ACTIVE
        )).willReturn(List.of(membership));
        given(reminderRepository.findRecordedMembershipIds(
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of(MEMBERSHIP_ID));
        serviceAt(CHECK_IN_WINDOW).sendDue();

        verifyNoInteractions(attendanceRecordRepository);
        verify(reminderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("동시 실행이 이력을 먼저 만들었으면 유니크 위반을 삼키고 보내지 않는다")
    void uniqueViolationPreventsDuplicateSend() {
        CohortMembership membership = membership(MEMBERSHIP_ID);
        prepareDue(ReminderType.CHECK_IN_BEFORE_DEADLINE, membership, List.of());
        given(reminderRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        serviceAt(CHECK_IN_WINDOW).sendDue();

        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("한 사람의 텔레그램 응답을 기다리지 않고 다음 사람 발송을 시작한다")
    void dispatchesNextDeliveryWithoutWaitingForPreviousResponse() {
        CohortMembership first = membership(MEMBERSHIP_ID);
        UUID secondUserId = UUID.randomUUID();
        CohortMembership second = membership(11L);
        given(first.getUserId()).willReturn(USER_ID);
        given(second.getUserId()).willReturn(secondUserId);
        prepareDue(
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                List.of(first, second),
                List.of()
        );
        CompletableFuture<Boolean> delayedDelivery = new CompletableFuture<>();
        given(sender.sendAsync(any()))
                .willReturn(delayedDelivery, CompletableFuture.completedFuture(true));

        assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> serviceAt(CHECK_IN_WINDOW).sendDue()
        );
        delayedDelivery.completeExceptionally(
                new IllegalStateException("telegram unavailable")
        );

        verify(sender, times(2)).sendAsync(any());
        verify(reminderRepository, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("손상된 기수 정책이 있어도 다음 기수 알림은 계속 처리한다")
    void isolatesDamagedPolicyPerCohort() {
        CohortAttendancePolicy damaged = CohortAttendancePolicy.create(
                99L,
                "invalid/timezone",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null,
                30,
                ADMIN_ID
        );
        CohortMembership membership = membership(MEMBERSHIP_ID);
        given(membership.getUserId()).willReturn(USER_ID);
        given(policyRepository.findAll()).willReturn(List.of(damaged, policy()));
        given(membershipRepository.findByCohortIdAndStatusOrderByRequestedAtAsc(
                COHORT_ID,
                CohortMembershipStatus.ACTIVE
        )).willReturn(List.of(membership));
        given(reminderRepository.findRecordedMembershipIds(
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of());
        given(attendanceRecordRepository.findByAttendanceDateAndCohortMembershipIdIn(
                ATTENDANCE_DATE,
                List.of(MEMBERSHIP_ID)
        )).willReturn(List.of());
        given(cohortRepository.findById(COHORT_ID)).willReturn(Optional.of(cohort()));
        given(reminderRepository.saveAndFlush(any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(sender.sendAsync(any())).willReturn(CompletableFuture.completedFuture(true));

        assertThatCode(() -> serviceAt(CHECK_IN_WINDOW).sendDue())
                .doesNotThrowAnyException();

        verify(sender).sendAsync(any());
        verify(membershipRepository, never())
                .findByCohortIdAndStatusOrderByRequestedAtAsc(
                        99L,
                        CohortMembershipStatus.ACTIVE
                );
    }

    private void prepareDue(
            ReminderType type,
            CohortMembership membership,
            List<AttendanceRecord> records
    ) {
        prepareDue(type, List.of(membership), records);
    }

    private void prepareDue(
            ReminderType type,
            CohortMembership membership,
            List<AttendanceRecord> records,
            CohortAttendancePolicy attendancePolicy
    ) {
        prepareDue(type, List.of(membership), records, attendancePolicy);
    }

    private void prepareDue(
            ReminderType type,
            List<CohortMembership> memberships,
            List<AttendanceRecord> records
    ) {
        prepareDue(type, memberships, records, policy());
    }

    private void prepareDue(
            ReminderType type,
            List<CohortMembership> memberships,
            List<AttendanceRecord> records,
            CohortAttendancePolicy attendancePolicy
    ) {
        List<Long> membershipIds = memberships.stream().map(CohortMembership::getId).toList();
        given(policyRepository.findAll()).willReturn(List.of(attendancePolicy));
        given(membershipRepository.findByCohortIdAndStatusOrderByRequestedAtAsc(
                COHORT_ID,
                CohortMembershipStatus.ACTIVE
        )).willReturn(memberships);
        given(reminderRepository.findRecordedMembershipIds(
                ATTENDANCE_DATE,
                type,
                ReminderChannel.TELEGRAM,
                membershipIds
        )).willReturn(List.of());
        given(attendanceRecordRepository.findByAttendanceDateAndCohortMembershipIdIn(
                ATTENDANCE_DATE,
                membershipIds
        )).willReturn(records);
        given(cohortRepository.findById(COHORT_ID)).willReturn(Optional.of(cohort()));
        given(reminderRepository.saveAndFlush(any()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private AttendanceReminderService serviceAt(Instant instant) {
        return new AttendanceReminderService(
                policyRepository,
                membershipRepository,
                cohortRepository,
                attendanceRecordRepository,
                reminderRepository,
                sender,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private CohortAttendancePolicy policy() {
        return CohortAttendancePolicy.create(
                COHORT_ID,
                "Asia/Seoul",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                null,
                30,
                ADMIN_ID
        );
    }

    private CohortAttendancePolicy afterMidnightPolicy() {
        return CohortAttendancePolicy.create(
                COHORT_ID,
                "Asia/Seoul",
                LocalTime.of(0, 3),
                LocalTime.of(3, 0),
                null,
                30,
                ADMIN_ID
        );
    }

    private CohortMembership membership(Long membershipId) {
        CohortMembership membership = mock(CohortMembership.class);
        given(membership.getId()).willReturn(membershipId);
        return membership;
    }

    private Cohort cohort() {
        return Cohort.create(
                "AIoT 3기",
                "테스트 기수",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                ADMIN_ID
        );
    }

    private AttendanceRecord checkedInRecord() {
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        record.checkIn(Instant.parse("2026-09-05T00:01:00Z"), AttendanceStatus.LATE, 1);
        return record;
    }
}
