package site.omagotchi.learningservice.attendance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("체류 구간")
class PresenceIntervalTest {

    @Test
    @DisplayName("종료 시각이 시작 시각보다 빠르면 종료를 거절한다")
    void rejectsEndBeforeStart() {
        PresenceInterval interval = PresenceInterval.start(
                1L,
                PresenceState.PRESENT,
                10L,
                Instant.parse("2026-08-31T01:00:00Z")
        );

        assertThatThrownBy(() -> interval.end(
                Instant.parse("2026-08-31T00:59:59Z")
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(interval.getEndedAt()).isNull();
    }

    @Test
    @DisplayName("이미 종료된 구간의 종료 재요청은 최초 종료 시각을 보존한다")
    void preservesFirstEndTimeOnRepeatedEnd() {
        PresenceInterval interval = PresenceInterval.start(
                1L,
                PresenceState.PRESENT,
                10L,
                Instant.parse("2026-08-31T01:00:00Z")
        );
        Instant firstEndedAt = Instant.parse("2026-08-31T02:00:00Z");

        interval.end(firstEndedAt);
        interval.end(Instant.parse("2026-08-31T03:00:00Z"));

        assertThat(interval.getEndedAt()).isEqualTo(firstEndedAt);
    }
}
