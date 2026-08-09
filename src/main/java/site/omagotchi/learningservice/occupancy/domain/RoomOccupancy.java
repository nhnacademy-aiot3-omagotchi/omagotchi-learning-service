package site.omagotchi.learningservice.occupancy.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 회의실 점유 세션 (MR-01, MR-08, MR-10, MR-11, MR-13).
 *
 * <p>주체 키가 둘인 것이 핵심이다. {@code occupierUserId}는 물리적 배타의 기준이고
 * ({@code uq_room_occupancies_one_active_per_user}가 계정 기준이라 다기수 담당자도
 * 방을 둘 잡을 수 없다), {@code occupierMembershipId}는 기수 도출용이다.
 * 둘을 함께 두는 이유는 복합 FK 하나로 멤버십 실재와 membership-user 소유 일치를
 * 동시에 보장하기 위해서다 (V5 주석).</p>
 *
 * <p>{@code cohortId}와 {@code teamId} 컬럼이 없는 것은 의도다 (ERD v3) — 기수는
 * {@code occupierMembershipId} 조인으로 도출하고, 점유는 항상 개인 단위라 팀 태그를
 * 두지 않는다 (MR-11).</p>
 *
 * <p>이 클래스는 {@code BusinessException}과 {@code ErrorCode}를 알지 못한다.
 * 조건은 {@code boolean}으로만 표현하고 오류 코드로 옮기는 것은 Application의 책임이다
 * ({@code TeamErrorCode} 주석 참고).</p>
 *
 * <p>{@code space} 파트의 {@code RoomOccupancyJpaEntity}가 같은 테이블에 붙어 있다.
 * JPA entity name이 서로 달라 충돌하지 않으며, 저쪽은 공간 목록의 사용 상태 파생
 * 계산용 읽기 전용이고 쓰기 주체는 이 클래스 하나다.</p>
 */
@Entity
@Table(name = "room_occupancies", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomOccupancy {

    /**
     * 기본 점유 시간 (MR-05).
     *
     * <p>인메모리 타이머를 쓰지 않는다 — {@code expires_at}을 DB에 기록하므로
     * 서버가 재시작되어도 만료 정보가 유실되지 않는다 (MR-13).</p>
     */
    public static final Duration DEFAULT_DURATION = Duration.ofHours(2);

    /** 1회 연장으로 늘어나는 시간 (MR-06). */
    public static final Duration EXTENSION_UNIT = Duration.ofMinutes(30);

    /**
     * 연장 가능 시점 — 만료 이 시간 전부터 허용한다 (MR-06).
     *
     * <p>{@link #EXTENSION_UNIT}과 값이 같은 것은 우연이다. 하나는 "얼마나 늘어나는가",
     * 다른 하나는 "언제부터 누를 수 있는가"라 정책이 따로 움직일 수 있으므로 상수를 나눈다.</p>
     */
    public static final Duration EXTENSION_WINDOW = Duration.ofMinutes(30);

    /**
     * 최대 연장 횟수 (MR-06). 기본 2시간 + 30분 × 2 = 최대 3시간.
     *
     * <p>{@code ck_room_occupancies_extension_count CHECK (extension_count BETWEEN 0 AND 2)}와
     * 같은 값이어야 한다. 여기만 늘리면 3회째 연장이 애플리케이션은 통과하고 DB에서 터진다.</p>
     */
    public static final short MAX_EXTENSION_COUNT = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "occupier_membership_id", nullable = false)
    private Long occupierMembershipId;

    @Column(name = "occupier_user_id", nullable = false)
    private UUID occupierUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OccupancyStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "extension_count", nullable = false)
    private short extensionCount;

    @Column(name = "reminder_sent_at")
    private OffsetDateTime reminderSentAt;

    /**
     * 점유를 시작한다 (MR-01).
     *
     * <p>{@code startedAt}을 DB DEFAULT에 맡기지 않고 명시하는 것이 요점이다.
     * DEFAULT는 {@code CURRENT_TIMESTAMP}라 한쪽만 앱에서 만들면 두 시각이 어긋나
     * "expires_at = started_at + 2시간"이 정확히 성립하지 않는다. 호출부는 두 값을
     * 반드시 같은 {@code now}에서 계산해 넘긴다.</p>
     */
    public static RoomOccupancy start(Long spaceId, Long occupierMembershipId, UUID occupierUserId,
                                      OffsetDateTime startedAt, OffsetDateTime expiresAt) {
        RoomOccupancy occupancy = new RoomOccupancy();
        occupancy.spaceId = Objects.requireNonNull(spaceId, "공간 ID는 필수");
        occupancy.occupierMembershipId = Objects.requireNonNull(occupierMembershipId, "점유자 맴버십 ID는 필수");
        occupancy.occupierUserId = Objects.requireNonNull(occupierUserId, "점유자 계정 ID는 필수");
        occupancy.status = OccupancyStatus.ACTIVE;
        occupancy.startedAt = Objects.requireNonNull(startedAt, "시작 시간은 필수");
        occupancy.expiresAt = Objects.requireNonNull(expiresAt, "만료 시각은 필수");
        occupancy.endedAt = null;
        occupancy.extensionCount = 0;
        return occupancy;
    }

    /** 사용 중인가. 부분 유니크 2종이 이 상태에만 걸린다. */
    public boolean isActive() {
        return status == OccupancyStatus.ACTIVE;
    }

    /**
     * 기준 시각에 이미 만료되었는가.
     *
     * <p>{@link #isActive()}와 별개인 것에 유의한다. 스케줄러(#9)가 아직 쓸어가지 않은
     * 행은 ACTIVE이면서 동시에 만료 상태일 수 있다 — 유니크 인덱스는 {@code status}만
     * 보고 {@code expires_at}은 보지 않기 때문이다.</p>
     */
    public boolean isExpiredAt(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** 요청자가 점유자 본인인가 (MR-06, MR-14의 403 판정에서 쓴다). */
    public boolean isOccupiedBy(UUID userId) {
        return occupierUserId.equals(userId);
    }

    /** 남은 시간(초). 만료 후에는 0이다. */
    public long remainingSeconds(OffsetDateTime now) {
        long seconds = Duration.between(now, expiresAt).toSeconds();

        return Math.max(seconds, 0L);
    }

    /**
     * 지금 연장할 수 있는 시점인가 (MR-06).
     *
     * <p>너무 이른 연장을 막는 것이 목적이다. 점유하자마자 최대치까지 늘려 두면 실제로
     * 쓰지 않는 시간이 선점되므로, 만료가 임박했을 때만 허용한다.</p>
     *
     * <p>이미 만료된 시각도 이 조건은 통과한다 — {@link #isExpiredAt(OffsetDateTime)}과
     * 함께 확인해야 한다. 두 조건을 하나로 합치지 않는 이유는 거부 사유가 달라
     * ("아직 이르다" vs "이미 끝났다") 오류 코드가 갈리기 때문이다.</p>
     */
    public boolean isWithinExtensionWindow(OffsetDateTime now) {
        return !now.isBefore(expiresAt.minus(EXTENSION_WINDOW));
    }

    /** 연장 횟수가 남았는가 (MR-06). {@code ck_room_occupancies_extension_count}가 최종 방어선이다. */
    public boolean hasRemainingExtension() {
        return extensionCount < MAX_EXTENSION_COUNT;
    }

    /**
     * 만료를 30분 미룬다 (MR-06, MR-12).
     *
     * <p><b>가산 기준은 {@code now}가 아니라 {@code expiresAt}이다.</b> {@code now} 기준으로
     * 더하면 늦게 연장할수록 총 사용 시간이 짧아져, 만료 직전까지 기다리는 쪽이 손해를 보는
     * 역전이 생긴다. 언제 누르든 결과가 같아야 한다.</p>
     *
     * <p>{@code reminderSentAt}을 되돌리는 것이 함께 있어야 한다 (MR-12). 같은 트랜잭션에서
     * NULL로 만들어야 새 만료 시각을 기준으로 임박 알림이 다시 나간다 — 남겨두면 연장한
     * 점유는 두 번 다시 알림을 받지 못한다.</p>
     *
     * <p>호출 전에 {@link #isWithinExtensionWindow}, {@link #isExpiredAt}을 확인해야
     * 한다 — "너무 이르다"·"이미 만료됐다"는 정상적인 사용자 흐름에서도 나오는 거부
     * 사유라 오류 코드로 옮기는 것은 Application의 책임이다. 반면 {@link #isActive()}·
     * {@link #hasRemainingExtension()}은 이 메서드 스스로 확인한다 — 종료된 점유의
     * 만료 시각을 미루는 것과 최대 횟수를 넘기는 것은 어떤 호출부를 거치든 성립해서는
     * 안 되는 불변식이기 때문이다. 특히 전자는 {@code extension_count}와 달리 지켜주는
     * DB CHECK가 없어 애플리케이션 계층의 사전 검증이 뚫리면 그대로 저장된다.</p>
     *
     * @throws IllegalStateException 종료된 점유이거나 연장 횟수를 이미 다 쓴 경우.
     *                                {@code BusinessException}이 아닌 것이 의도다 — 이
     *                                예외는 정상 사용자 흐름이 아니라 호출부가 사전
     *                                검증을 건너뛴 계약 위반을 뜻한다.
     */
    public void extend() {
        if (!isActive()) {
            throw new IllegalStateException("종료된 점유는 연장할 수 없습니다.");
        }
        if (!hasRemainingExtension()) {
            throw new IllegalStateException("연장 횟수를 모두 사용했습니다.");
        }
        this.expiresAt = expiresAt.plus(EXTENSION_UNIT);
        this.extensionCount++;
        this.reminderSentAt = null;
    }

    /**
     * 점유를 반납한다 (MR-14).
     *
     * <p>{@code status}와 {@code endedAt}을 반드시 함께 세팅한다 —
     * {@code ck_room_occupancies_end}가 {@code (status = 'ACTIVE') = (ended_at IS NULL)}을
     * 강제하므로 한쪽만 바꾸면 커밋이 거부된다.</p>
     *
     * <p>종료 상태는 최종 상태이며 재전이가 없다. 이미 끝난 점유에 다시 호출하면 아무것도
     * 바꾸지 않고 {@code false}를 돌려준다 — 스케줄러가 EXPIRED로 바꾼 직후 도착한 반납
     * 요청이 종료 사유를 RELEASED로 덮어쓰면 통계가 틀어진다.</p>
     *
     * @return 이번 호출로 반납됐으면 {@code true}, 이미 종료된 상태였으면 {@code false}
     */
    public boolean release(OffsetDateTime endedAt) {
        if (!isActive()) {
            return false;
        }
        this.status = OccupancyStatus.RELEASED;
        this.endedAt = Objects.requireNonNull(endedAt, "종료 시각은 필수");
        return true;
    }
}
