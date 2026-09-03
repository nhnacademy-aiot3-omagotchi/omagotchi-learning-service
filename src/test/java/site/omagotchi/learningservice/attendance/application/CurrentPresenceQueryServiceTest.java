package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.port.AttendancePresenceQuery;
import site.omagotchi.learningservice.attendance.application.result.CurrentPresenceResult;
import site.omagotchi.learningservice.attendance.domain.PresenceState;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("현재 위치 조회 서비스")
class CurrentPresenceQueryServiceTest {

    private static final Long COHORT_ID = 7L;
    private static final Long MEMBERSHIP_ID = 11L;
    private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2026, 9, 2);
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private AttendancePresenceQuery attendancePresenceQuery;

    private CurrentPresenceQueryService service;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-02T01:00:00Z"),
            ZoneOffset.UTC
    );

    @BeforeEach
    void setUp() {
        service = new CurrentPresenceQueryService(
                cohortAccessService,
                attendancePresenceQuery,
                clock
        );
    }

    @Test
    @DisplayName("활성 소속의 가장 최근 열린 체류구간을 현재 위치로 반환한다")
    void returnsLatestOpenPresenceOfActiveMembership() {
        CurrentPresenceResult latest = new CurrentPresenceResult(
                301L,
                PresenceState.PRESENT,
                Instant.parse("2026-09-02T01:00:00Z")
        );
        when(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .thenReturn(MEMBERSHIP_ID);
        when(attendancePresenceQuery.findCurrentPresences(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE
        ))
                .thenReturn(List.of(latest));

        var result = service.findCurrentPresence(COHORT_ID, USER_ID);

        assertThat(result).contains(latest);
        verify(cohortAccessService).requireActiveMembershipId(COHORT_ID, USER_ID);
        verify(attendancePresenceQuery).findCurrentPresences(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE
        );
    }

    @Test
    @DisplayName("체크인 후 공간을 아직 선택하지 않았으면 현재 위치가 없다")
    void returnsEmptyWhenNoOpenPresenceExists() {
        when(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .thenReturn(MEMBERSHIP_ID);
        when(attendancePresenceQuery.findCurrentPresences(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE
        ))
                .thenReturn(List.of());

        assertThat(service.findCurrentPresence(COHORT_ID, USER_ID)).isEmpty();
    }
}
