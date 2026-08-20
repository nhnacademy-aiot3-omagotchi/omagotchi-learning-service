package site.omagotchi.learningservice.occupancy.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 점유 세션의 참여자 (MR-27, MR-29, MR-30, MR-32).
 *
 * <p>구간 모델이다 — {@code leftAt IS NULL}이 참여 중, 값이 있으면 이탈·제외·종료 시각이다.
 * 이탈해도 행을 삭제하지 않으며, 재합류는 새 행이 아니라 기존 행의 {@code leftAt}을
 * NULL로 되돌린다 (결정 #30). 행을 지우면 참여 이력이 사라진다.</p>
 *
 * <p>{@code uq_occupancy_participants_one_active}가 {@code user_id} 기준이라
 * "사람당 활성 참여 1건"(MR-30)이 DB에서 보장된다. 점유 시작이 점유자를 참여자로
 * 자동 등록하므로(MR-27), 다른 회의에 참여 중인 사람의 점유 요청은 이 유니크에서
 * 걸린다 — 별도 선검사 없이 409가 나간다.</p>
 */
@Entity
@Table(name = "occupancy_participants", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OccupancyParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occupancy_id", nullable = false)
    private Long occupancyId;

    @Column(name = "cohort_membership_id", nullable = false)
    private Long cohortMembershipId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    /**
     * 참여자 행을 만든다.
     *
     * <p>점유 시작 시 점유자 본인을 등록하는 경로(MR-27)와 참여자 추가(#6)가 공용한다.
     * 참여자의 기수 정합(MR-33)은 DB가 보장하지 않으므로 호출부의 책임이다.</p>
     */
    public static OccupancyParticipant join(Long occupancyId, Long cohortMembershipId,
                                            UUID userId, OffsetDateTime joinedAt) {
        OccupancyParticipant participants = new OccupancyParticipant();

        participants.occupancyId = Objects.requireNonNull(occupancyId, "점유 ID는 필수");
        participants.cohortMembershipId = Objects.requireNonNull(cohortMembershipId, "맴버십 아이디는 필수");
        participants.userId = Objects.requireNonNull(userId, "계정 아이디는 필수");
        participants.joinedAt = Objects.requireNonNull(joinedAt, "참여 시각은 필수");
        participants.leftAt = null;

        return participants;
    }

    /** 현재 참여 중인가. */
    public boolean isActive() {
        return leftAt == null;
    }

    /**
     * 이탈·제외를 기록한다 (MR-31, MR-32).
     *
     * <p>행을 지우지 않고 {@code leftAt}만 찍는 것이 구간 모델의 요점이다. 이미 이탈한
     * 행에 다시 찍지 않는 것도 중요하다 — 최초 이탈 시각이 참여 구간의 끝이고, 덮어쓰면
     * 그 사람이 실제보다 오래 있었던 것으로 기록된다.</p>
     *
     * @return 이번 호출로 이탈 처리됐으면 {@code true}, 이미 이탈한 상태였으면 {@code false}
     */
    public boolean leave(OffsetDateTime leftAt) {
        if (this.leftAt != null) {
            return false;
        }
        this.leftAt = Objects.requireNonNull(leftAt, "이탈 시각은 필수");
        return true;
    }

    /**
     * 재합류한다 (결정 #30).
     *
     * <p>새 행을 만들지 않고 기존 행의 {@code leftAt}을 되돌린다.
     * {@code uq_occupancy_participants_pair}가 점유당 사람 1행이라 새 행은 애초에 들어가지
     * 않으며, 행을 지우고 다시 넣으면 참여 이력이 사라진다.</p>
     *
     * <p>{@code joinedAt}을 갱신하지 않는 것은 의도다 — 이 사람이 이 회의에 처음 들어온
     * 시각이 최초 참여 시각이고, 재합류마다 앞당기면 참여 구간의 시작을 잃는다.</p>
     */
    public void rejoin() {
        this.leftAt = null;
    }
}
