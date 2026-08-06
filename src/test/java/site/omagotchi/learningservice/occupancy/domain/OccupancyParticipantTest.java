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
    @DisplayName("이탈하면 이탈 시각이 기록된다.")
    void test3() {
        OccupancyParticipant participant = join();

        assertThat(participant.leave(JOINED_AT.plusMinutes(30))).isTrue();

        assertThat(participant.getLeftAt()).isEqualTo(JOINED_AT.plusMinutes(30));
        assertThat(participant.isActive()).isFalse();
    }

    /**
     * 최초 이탈 시각이 참여 구간의 끝이다. 덮어쓰면 그 사람이 실제보다 오래 있었던 것으로
     * 기록되므로, 두 번째 호출은 아무것도 바꾸지 않고 {@code false}를 돌려준다.
     */
    @Test
    @DisplayName("이미 이탈했으면 이탈 시각을 덮어쓰지 않는다.")
    void test4() {
        OccupancyParticipant participant = join();
        participant.leave(JOINED_AT.plusMinutes(30));

        assertThat(participant.leave(JOINED_AT.plusHours(2))).isFalse();

        assertThat(participant.getLeftAt()).isEqualTo(JOINED_AT.plusMinutes(30));
    }

    /** 재합류마다 joinedAt을 앞당기면 이 사람이 회의에 처음 들어온 시각을 잃는다. */
    @Test
    @DisplayName("재합류하면 최초 참여 시각을 유지한 채 이탈 시각만 지워진다.")
    void test5() {
        OccupancyParticipant participant = join();
        participant.leave(JOINED_AT.plusMinutes(30));

        participant.rejoin();

        assertThat(participant.getLeftAt()).isNull();
        assertThat(participant.isActive()).isTrue();
        assertThat(participant.getJoinedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    @DisplayName("이탈 시각 없이는 이탈할 수 없다.")
    void test6() {
        OccupancyParticipant participant = join();

        assertThatThrownBy(() -> participant.leave(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("참여에 필요한 값이 비어 있으면 만들 수 없다.")
    void test7() {
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
