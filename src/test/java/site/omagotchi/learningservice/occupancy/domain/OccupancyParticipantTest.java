package site.omagotchi.learningservice.occupancy.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 참여자 구간 모델 (MR-27, MR-30, MR-32).
 *
 * <p>{@code leftAt}이 NULL인지가 곧 참여 여부다. 이탈해도 행을 지우지 않으며 재합류는
 * 새 행이 아니라 기존 행의 {@code leftAt}을 되돌린다 — 행을 지우면 참여 이력이 사라진다.</p>
 */
class OccupancyParticipantTest {

    private static final OffsetDateTime JOINED_AT =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("참여하면 이탈 시각 없이 참여 중으로 시작한다.")
    void test1() {
        OccupancyParticipant participant = join();

        assertThat(participant.getOccupancyId()).isEqualTo(100L);
        assertThat(participant.getCohortMembershipId()).isEqualTo(10L);
        assertThat(participant.getUserId()).isEqualTo(USER_ID);
        assertThat(participant.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(participant.getLeftAt()).isNull();
        assertThat(participant.isActive()).isTrue();
    }

    /**
     * 이탈·제외·점유 종료를 모두 {@code leftAt} 기록으로 표현한다. 이탈 메서드가 붙기
     * 전이라 값을 직접 세팅해 판정만 고정한다.
     */
    @Test
    @DisplayName("이탈 시각이 기록되면 참여 중이 아니다.")
    void test2() {
        OccupancyParticipant participant = join();

        ReflectionTestUtils.setField(participant, "leftAt", JOINED_AT.plusMinutes(30));

        assertThat(participant.isActive()).isFalse();
    }

    @Test
    @DisplayName("참여에 필요한 값이 비어 있으면 만들 수 없다.")
    void test3() {
        assertThatThrownBy(() -> OccupancyParticipant.join(null, 10L, USER_ID, JOINED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OccupancyParticipant.join(100L, null, USER_ID, JOINED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OccupancyParticipant.join(100L, 10L, null, JOINED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OccupancyParticipant.join(100L, 10L, USER_ID, null))
                .isInstanceOf(NullPointerException.class);
    }

    private OccupancyParticipant join() {
        return OccupancyParticipant.join(100L, 10L, USER_ID, JOINED_AT);
    }
}
