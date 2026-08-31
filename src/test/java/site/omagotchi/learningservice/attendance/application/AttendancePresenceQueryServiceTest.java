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

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
}
