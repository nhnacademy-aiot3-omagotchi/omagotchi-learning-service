package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.port.AttendancePresenceQuery;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.attendance.application.result.OpenUserPresenceView;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("현재 재실 조회 서비스")
class AttendancePresenceQueryServiceTest {

    @Mock
    private AttendancePresenceQuery attendancePresenceQuery;

    @InjectMocks
    private AttendancePresenceQueryService service;

    @Test
    @DisplayName("가장 최근 열린 구간의 출결 ID와 소속 ID를 함께 반환한다")
    void returnsAttendanceIdOfLatestOpenPresence() {
        UUID userId = UUID.randomUUID();
        OpenPresenceView latest = new OpenPresenceView(
                101L,
                201L,
                Instant.parse("2026-08-31T02:00:00Z")
        );
        OpenPresenceView older = new OpenPresenceView(
                102L,
                202L,
                Instant.parse("2026-08-31T01:00:00Z")
        );
        when(attendancePresenceQuery.findOpenPresences(userId))
                .thenReturn(List.of(latest, older));

        OpenPresenceView result = service.findOpenPresence(userId).orElseThrow();

        assertThat(result.attendanceId()).isEqualTo(101L);
        assertThat(result.cohortMembershipId()).isEqualTo(201L);
        assertThat(result.startedAt()).isEqualTo(latest.startedAt());
    }

    @Test
    @DisplayName("계정별 일괄 조회도 선택한 최신 구간의 출결 ID를 보존한다")
    void preservesAttendanceIdInBatchPresenceLookup() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        LinkedHashSet<UUID> userIds = new LinkedHashSet<>(List.of(
                firstUserId,
                secondUserId
        ));
        when(attendancePresenceQuery.findOpenPresences(userIds)).thenReturn(List.of(
                new OpenUserPresenceView(
                        firstUserId,
                        301L,
                        401L,
                        Instant.parse("2026-08-31T03:00:00Z")
                ),
                new OpenUserPresenceView(
                        firstUserId,
                        302L,
                        402L,
                        Instant.parse("2026-08-31T02:00:00Z")
                ),
                new OpenUserPresenceView(
                        secondUserId,
                        303L,
                        403L,
                        Instant.parse("2026-08-31T01:00:00Z")
                )
        ));

        var results = service.findOpenPresences(userIds);

        assertThat(results.get(firstUserId).attendanceId()).isEqualTo(301L);
        assertThat(results.get(firstUserId).cohortMembershipId()).isEqualTo(401L);
        assertThat(results.get(secondUserId).attendanceId()).isEqualTo(303L);
        assertThat(results.get(secondUserId).cohortMembershipId()).isEqualTo(403L);
    }

    @Test
    @DisplayName("소속별 조회는 다기수 사용자의 참여 소속과 같은 출결 ID를 선택한다")
    void selectsLatestPresenceByExactMembership() {
        when(attendancePresenceQuery.findOpenPresencesByMembershipIds(List.of(401L, 402L)))
                .thenReturn(List.of(
                        new OpenPresenceView(301L, 401L, Instant.parse("2026-08-31T03:00:00Z")),
                        new OpenPresenceView(302L, 401L, Instant.parse("2026-08-31T02:00:00Z")),
                        new OpenPresenceView(303L, 402L, Instant.parse("2026-08-31T01:00:00Z"))
                ));

        Map<Long, OpenPresenceView> results = service
                .findOpenPresencesByMembershipIds(List.of(401L, 402L));

        assertThat(results.get(401L).attendanceId()).isEqualTo(301L);
        assertThat(results.get(402L).attendanceId()).isEqualTo(303L);
    }

    @Test
    @DisplayName("회의 이탈 조회는 더 최신 PRESENT가 있어도 같은 회의실의 열린 MEETING을 반환한다")
    void selectsOpenMeetingInsteadOfLatestOpenPresence() {
        when(attendancePresenceQuery.findOpenMeetingPresencesByMembershipIds(
                List.of(401L), 701L))
                .thenReturn(List.of(
                        new OpenPresenceView(
                                301L,
                                401L,
                                Instant.parse("2026-08-30T20:00:00Z")
                        )
                ));

        Map<Long, OpenPresenceView> results = service
                .findOpenMeetingPresencesByMembershipIds(List.of(401L), 701L);

        assertThat(results.get(401L).attendanceId()).isEqualTo(301L);
    }

    @Test
    @DisplayName("같은 소속과 회의실에 열린 MEETING이 둘이면 최신 하나를 숨기지 않고 실패한다")
    void rejectsDuplicateOpenMeetingsForMembership() {
        when(attendancePresenceQuery.findOpenMeetingPresencesByMembershipIds(
                List.of(401L), 701L))
                .thenReturn(List.of(
                        new OpenPresenceView(
                                301L,
                                401L,
                                Instant.parse("2026-08-30T20:00:00Z")
                        ),
                        new OpenPresenceView(
                                302L,
                                401L,
                                Instant.parse("2026-08-31T04:00:00Z")
                        )
                ));

        assertThatThrownBy(() -> service.findOpenMeetingPresencesByMembershipIds(
                List.of(401L), 701L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isSameAs(AttendanceErrorCode.PRESENCE_INTERVAL_INCONSISTENT));
    }
}
