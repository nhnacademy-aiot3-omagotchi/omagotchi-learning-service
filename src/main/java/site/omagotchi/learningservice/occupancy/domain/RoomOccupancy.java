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
}
