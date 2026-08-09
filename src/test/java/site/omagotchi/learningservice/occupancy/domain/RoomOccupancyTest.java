package site.omagotchi.learningservice.occupancy.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 점유 세션의 도메인 규칙 (MR-01, MR-05, MR-13).
 *
 * <p>여기서는 시각 계산과 상태 판정만 고정한다. "expires_at = started_at + 2시간"을
 * 실제로 성립시키는 것은 두 값을 같은 {@code now}에서 만드는 호출부의 책임이므로
 * {@code RoomOccupancyServiceTest}가 담당한다.</p>
 */
class RoomOccupancyTest {

    private static final OffsetDateTime STARTED_AT =
            OffsetDateTime.of(2026, 7, 24, 10, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime EXPIRES_AT = STARTED_AT.plusHours(2);
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("점유를 시작하면 ACTIVE 상태로 연장 횟수 0에서 출발한다.")
    void test1() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getExtensionCount()).isZero();
        assertThat(occupancy.isActive()).isTrue();
    }

    /** ck_room_occupancies_end 가 (status='ACTIVE') = (ended_at IS NULL) 을 강제한다. */
    @Test
    @DisplayName("시작 직후에는 종료 시각이 비어 있다.")
    void test2() {
        assertThat(start().getEndedAt()).isNull();
    }

    @Test
    @DisplayName("점유 주체로 멤버십과 계정을 함께 보관한다.")
    void test3() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.getOccupierMembershipId()).isEqualTo(10L);
        assertThat(occupancy.getOccupierUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("기본 점유 시간은 2시간이다.")
    void test4() {
        assertThat(RoomOccupancy.DEFAULT_DURATION).isEqualTo(Duration.ofHours(2));
    }

    /**
     * 만료 판정이 ACTIVE 여부와 별개인 것이 요점이다. 스케줄러가 아직 쓸어가지 않은
     * 행은 ACTIVE 이면서 동시에 만료 상태일 수 있다.
     */
    @Test
    @DisplayName("만료 시각과 같은 순간부터 만료로 본다.")
    void test5() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.isExpiredAt(EXPIRES_AT.minusSeconds(1))).isFalse();
        assertThat(occupancy.isExpiredAt(EXPIRES_AT)).isTrue();
        assertThat(occupancy.isExpiredAt(EXPIRES_AT.plusSeconds(1))).isTrue();
        assertThat(occupancy.isActive()).isTrue();
    }

    @Test
    @DisplayName("남은 시간은 만료까지의 초이며 만료 후에는 0이다.")
    void test6() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.remainingSeconds(STARTED_AT)).isEqualTo(7200L);
        assertThat(occupancy.remainingSeconds(EXPIRES_AT.minusMinutes(30))).isEqualTo(1800L);
        assertThat(occupancy.remainingSeconds(EXPIRES_AT)).isZero();
        assertThat(occupancy.remainingSeconds(EXPIRES_AT.plusHours(1))).isZero();
    }

    /**
     * 종료 상태는 최종 상태이며 재전이가 없다. 반납·연장이 붙기 전이라 상태를 직접
     * 세팅해 판정만 고정한다 — 전이 자체의 검증은 그 기능과 함께 온다.
     */
    @Test
    @DisplayName("종료된 점유는 사용 중이 아니다.")
    void test7() {
        RoomOccupancy occupancy = start();

        for (OccupancyStatus ended : new OccupancyStatus[]{
                OccupancyStatus.RELEASED, OccupancyStatus.EXPIRED, OccupancyStatus.FORCE_RELEASED}) {
            ReflectionTestUtils.setField(occupancy, "status", ended);
            assertThat(occupancy.isActive()).isFalse();
        }
    }

    @Test
    @DisplayName("점유자 판정은 멤버십이 아니라 계정 기준이다.")
    void test8() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.isOccupiedBy(USER_ID)).isTrue();
        assertThat(occupancy.isOccupiedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("시작에 필요한 값이 비어 있으면 만들 수 없다.")
    void test9() {
        assertThatThrownBy(() -> RoomOccupancy.start(null, 10L, USER_ID, STARTED_AT, EXPIRES_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoomOccupancy.start(1L, null, USER_ID, STARTED_AT, EXPIRES_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoomOccupancy.start(1L, 10L, null, STARTED_AT, EXPIRES_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoomOccupancy.start(1L, 10L, USER_ID, null, EXPIRES_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoomOccupancy.start(1L, 10L, USER_ID, STARTED_AT, null))
                .isInstanceOf(NullPointerException.class);
    }

    private RoomOccupancy start() {
        return RoomOccupancy.start(1L, 10L, USER_ID, STARTED_AT, EXPIRES_AT);
    }
}
