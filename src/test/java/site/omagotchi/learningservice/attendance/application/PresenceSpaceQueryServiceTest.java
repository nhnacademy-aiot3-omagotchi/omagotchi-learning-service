package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.port.AttendanceSpacePresenceQuery;
import site.omagotchi.learningservice.attendance.application.result.SpacePresenceSummary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceSpaceQueryServiceTest {

    @Mock
    private AttendanceSpacePresenceQuery query;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-31T18:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    @DisplayName("KST 오전 4시 집계일을 적용하고 중복 공간 ID를 한 번만 조회한다")
    void usesKstFourAmAggregationDateAndRemovesDuplicateSpaceIds() {
        PresenceSpaceQueryService service = new PresenceSpaceQueryService(query, clock);
        Map<Long, SpacePresenceSummary> expected = Map.of(
                10L, new SpacePresenceSummary(2L, 1L)
        );
        when(query.summarize(Set.of(10L), LocalDate.of(2026, 8, 31)))
                .thenReturn(expected);

        Map<Long, SpacePresenceSummary> result = service.summarize(
                List.of(10L, 10L)
        );

        assertThat(result).isEqualTo(expected);
        verify(query).summarize(Set.of(10L), LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("공간 ID가 없으면 저장소를 조회하지 않고 빈 집계를 반환한다")
    void returnsEmptySummaryForNullSpaceIdWithoutQuery() {
        PresenceSpaceQueryService service = new PresenceSpaceQueryService(query, clock);

        assertThat(service.summarize((Long) null)).isEqualTo(SpacePresenceSummary.empty());
    }
}
