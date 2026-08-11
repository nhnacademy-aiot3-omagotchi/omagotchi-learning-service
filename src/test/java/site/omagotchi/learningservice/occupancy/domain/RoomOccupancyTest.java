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
    void startBeginsActiveWithZeroExtensions() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getExtensionCount()).isZero();
        assertThat(occupancy.isActive()).isTrue();
    }

    /** ck_room_occupancies_end 가 (status='ACTIVE') = (ended_at IS NULL) 을 강제한다. */
    @Test
    @DisplayName("시작 직후에는 종료 시각이 비어 있다.")
    void endedAtIsNullRightAfterStart() {
        assertThat(start().getEndedAt()).isNull();
    }

    @Test
    @DisplayName("점유 주체로 멤버십과 계정을 함께 보관한다.")
    void storesBothMembershipAndUserIdAsOccupier() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.getOccupierMembershipId()).isEqualTo(10L);
        assertThat(occupancy.getOccupierUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("기본 점유 시간은 2시간이다.")
    void defaultDurationIsTwoHours() {
        assertThat(RoomOccupancy.DEFAULT_DURATION).isEqualTo(Duration.ofHours(2));
    }

    /**
     * 만료 판정이 ACTIVE 여부와 별개인 것이 요점이다. 스케줄러가 아직 쓸어가지 않은
     * 행은 ACTIVE 이면서 동시에 만료 상태일 수 있다.
     */
    @Test
    @DisplayName("만료 시각과 같은 순간부터 만료로 본다.")
    void isExpiredAtTheExactExpiryMoment() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.isExpiredAt(EXPIRES_AT.minusSeconds(1))).isFalse();
        assertThat(occupancy.isExpiredAt(EXPIRES_AT)).isTrue();
        assertThat(occupancy.isExpiredAt(EXPIRES_AT.plusSeconds(1))).isTrue();
        assertThat(occupancy.isActive()).isTrue();
    }

    @Test
    @DisplayName("남은 시간은 만료까지의 초이며 만료 후에는 0이다.")
    void remainingSecondsIsZeroAfterExpiry() {
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
    void endedStatusIsNotActive() {
        RoomOccupancy occupancy = start();

        for (OccupancyStatus ended : new OccupancyStatus[]{
                OccupancyStatus.RELEASED, OccupancyStatus.EXPIRED, OccupancyStatus.FORCE_RELEASED}) {
            ReflectionTestUtils.setField(occupancy, "status", ended);
            assertThat(occupancy.isActive()).isFalse();
        }
    }

    @Test
    @DisplayName("점유자 판정은 멤버십이 아니라 계정 기준이다.")
    void occupiedByChecksUserIdNotMembership() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.isOccupiedBy(USER_ID)).isTrue();
        assertThat(occupancy.isOccupiedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("시작에 필요한 값이 비어 있으면 만들 수 없다.")
    void cannotStartWithMissingRequiredValues() {
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

    // ────────────────────────────── 연장 (MR-06, MR-12) ──────────────────────────────

    @Test
    @DisplayName("연장 가능 시점은 만료 30분 전부터다.")
    void extensionWindowStartsThirtyMinutesBeforeExpiry() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.isWithinExtensionWindow(EXPIRES_AT.minusMinutes(31))).isFalse();
        assertThat(occupancy.isWithinExtensionWindow(EXPIRES_AT.minusMinutes(30))).isTrue();
        assertThat(occupancy.isWithinExtensionWindow(EXPIRES_AT.minusMinutes(29))).isTrue();
    }

    /**
     * 가산 기준이 {@code now}가 아니라 {@code expiresAt}인 것을 고정한다. {@code now}
     * 기준이면 늦게 누를수록 총 사용 시간이 짧아져 만료 직전까지 기다린 쪽이 손해를 본다.
     */
    @Test
    @DisplayName("연장 결과는 언제 눌렀는지와 무관하게 만료 시각의 30분 뒤다.")
    void extendResultIsThirtyMinutesAfterExpiryRegardlessOfWhenCalled() {
        RoomOccupancy early = start();
        RoomOccupancy late = start();

        early.extend();
        late.extend();

        assertThat(early.getExpiresAt()).isEqualTo(EXPIRES_AT.plusMinutes(30));
        assertThat(late.getExpiresAt()).isEqualTo(EXPIRES_AT.plusMinutes(30));
    }

    @Test
    @DisplayName("연장하면 횟수가 하나 늘어난다.")
    void extendIncrementsExtensionCount() {
        RoomOccupancy occupancy = start();

        occupancy.extend();

        assertThat(occupancy.getExtensionCount()).isEqualTo((short) 1);
    }

    /** 남겨두면 연장한 점유는 두 번 다시 임박 알림을 받지 못한다 (MR-12). */
    @Test
    @DisplayName("연장하면 임박 알림 발송 기록이 지워진다.")
    void extendClearsReminderSentAt() {
        RoomOccupancy occupancy = start();
        ReflectionTestUtils.setField(occupancy, "reminderSentAt", EXPIRES_AT.minusMinutes(10));

        occupancy.extend();

        assertThat(occupancy.getReminderSentAt()).isNull();
    }

    @Test
    @DisplayName("연장은 두 번까지만 남아 있다.")
    void hasRemainingExtensionUpToTwoTimes() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.hasRemainingExtension()).isTrue();
        occupancy.extend();
        assertThat(occupancy.hasRemainingExtension()).isTrue();
        occupancy.extend();
        assertThat(occupancy.hasRemainingExtension()).isFalse();

        assertThat(occupancy.getExtensionCount()).isEqualTo(RoomOccupancy.MAX_EXTENSION_COUNT);
        assertThat(occupancy.getExpiresAt()).isEqualTo(EXPIRES_AT.plusHours(1));
    }

    /** 여기 값이 DB CHECK 제약과 어긋나면 3회째 연장이 앱은 통과하고 DB에서 터진다. */
    @Test
    @DisplayName("연장 상수는 30분 단위·최대 2회다.")
    void extensionConstantsAreThirtyMinutesAndMaxTwo() {
        assertThat(RoomOccupancy.EXTENSION_UNIT).isEqualTo(Duration.ofMinutes(30));
        assertThat(RoomOccupancy.EXTENSION_WINDOW).isEqualTo(Duration.ofMinutes(30));
        assertThat(RoomOccupancy.MAX_EXTENSION_COUNT).isEqualTo((short) 2);
    }

    /**
     * {@code extension_count}를 지켜주는 {@code ck_room_occupancies_extension_count}가
     * 있긴 하지만, 그건 커밋 시점 최종 방어선이다. Application의 사전 검증을 우회해
     * {@code extend()}를 세 번째 호출해도 여기서 막혀야 한다.
     */
    @Test
    @DisplayName("연장 횟수를 다 쓰면 더 이상 연장할 수 없다.")
    void cannotExtendAfterUsingUpAllExtensions() {
        RoomOccupancy occupancy = start();
        occupancy.extend();
        occupancy.extend();

        assertThatThrownBy(occupancy::extend)
                .isInstanceOf(IllegalStateException.class);

        assertThat(occupancy.getExtensionCount()).isEqualTo(RoomOccupancy.MAX_EXTENSION_COUNT);
        assertThat(occupancy.getExpiresAt()).isEqualTo(EXPIRES_AT.plusHours(1));
    }

    /**
     * {@code expires_at}은 {@code extension_count}와 달리 지켜주는 DB CHECK가 없다.
     * Application의 사전 검증이 뚫리면 종료된 점유의 만료 시각이 그대로 저장되므로,
     * 이 방어는 도메인 메서드 자신에게만 있다.
     */
    @Test
    @DisplayName("종료된 점유는 연장할 수 없다.")
    void cannotExtendEndedOccupancy() {
        RoomOccupancy occupancy = start();
        occupancy.release(EXPIRES_AT.minusMinutes(10));

        assertThatThrownBy(occupancy::extend)
                .isInstanceOf(IllegalStateException.class);

        assertThat(occupancy.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(occupancy.getExtensionCount()).isZero();
    }

    // ────────────────────────────── 반납 (MR-14) ──────────────────────────────

    /** ck_room_occupancies_end 때문에 status와 ended_at은 반드시 함께 바뀌어야 한다. */
    @Test
    @DisplayName("반납하면 상태와 종료 시각이 함께 기록된다.")
    void releaseRecordsStatusAndEndedAtTogether() {
        RoomOccupancy occupancy = start();

        assertThat(occupancy.release(EXPIRES_AT.minusMinutes(10))).isTrue();

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.RELEASED);
        assertThat(occupancy.getEndedAt()).isEqualTo(EXPIRES_AT.minusMinutes(10));
        assertThat(occupancy.isActive()).isFalse();
    }

    /**
     * 스케줄러가 EXPIRED로 바꾼 직후 도착한 반납이 종료 사유를 덮어쓰면 통계가 틀어진다.
     * 종료 상태는 최종 상태이며 재전이가 없다.
     */
    @Test
    @DisplayName("이미 종료된 점유는 반납해도 상태가 바뀌지 않는다.")
    void releaseDoesNotChangeAlreadyEndedStatus() {
        RoomOccupancy occupancy = start();
        ReflectionTestUtils.setField(occupancy, "status", OccupancyStatus.EXPIRED);
        ReflectionTestUtils.setField(occupancy, "endedAt", EXPIRES_AT);

        assertThat(occupancy.release(EXPIRES_AT.plusMinutes(5))).isFalse();

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.EXPIRED);
        assertThat(occupancy.getEndedAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("종료 시각 없이는 반납할 수 없다.")
    void cannotReleaseWithoutEndedAt() {
        assertThatThrownBy(() -> start().release(null))
                .isInstanceOf(NullPointerException.class);
    }

    private RoomOccupancy start() {
        return RoomOccupancy.start(1L, 10L, USER_ID, STARTED_AT, EXPIRES_AT);
    }
}
