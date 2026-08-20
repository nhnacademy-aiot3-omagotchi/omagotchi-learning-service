package site.omagotchi.learningservice.occupancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 공실 알림 신청 (MR-02, MR-03, MR-15, MR-34).
 *
 * <p>일회성 의사표시다 — "이 방 비면 한 번 알려줘". {@code notifiedAt} 하나가 상태
 * 머신 전체이며, 대기({@code NULL}) → 소진(값 기록)의 단방향이다. 취소·정리는 상태
 * 전이가 아니라 행 물리 삭제다.</p>
 *
 * <p><b>계정이 아니라 멤버십을 키로 잡는다</b> (ERD v3). 점유·참여는 "사람은 한 방에만"이라는
 * 물리적 배타라 {@code user_id} 기준이지만, 이쪽은 기수 스코프 의사표시다. 덕분에 기수
 * 종료 정리(CE-02)가 "그 기수 멤버십의 신청 삭제" 한 줄이 되고, 다기수 담당자가 두 멤버십으로
 * 신청하면 알림도 두 번 받는다 — 본인이 두 번 신청한 결과라 정상 동작이다.</p>
 *
 * <p>재신청은 새 행이다. {@code uq_vacancy_alerts_waiting}이 대기 중만 제약하므로 소진된
 * 신청은 중복 판정에 들어가지 않는다 — {@code OccupancyParticipant}가 행을 재사용하는 것과
 * 반대인데, 저쪽은 참여 <b>구간</b>이고 이쪽은 매번 독립된 의사표시이기 때문이다.</p>
 */
@Entity
@Table(name = "vacancy_alerts", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VacancyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "cohort_membership_id", nullable = false)
    private Long cohortMembershipId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "notified_at")
    private OffsetDateTime notifiedAt;

    /**
     * 신청 행을 만든다.
     *
     * <p>"빈 방이 아닌지"·"본인 방이 아닌지"는 여기서 보지 않는다. 둘 다 이 aggregate
     * 밖의 점유 상태라 스스로 확인할 수 없다.</p>
     */
    public static VacancyAlert request(Long spaceId, Long cohortMembershipId, OffsetDateTime createdAt) {
        VacancyAlert alert = new VacancyAlert();

        alert.spaceId = Objects.requireNonNull(spaceId, "공간 ID는 필수");
        alert.cohortMembershipId = Objects.requireNonNull(cohortMembershipId, "기수 소속 ID는 필수");
        alert.createdAt = Objects.requireNonNull(createdAt, "신청 시각은 필수");
        alert.notifiedAt = null;

        return alert;
    }

    /** 아직 발송되지 않은 신청인가. 취소·정리 대상 판정의 기준이다. */
    public boolean isWaiting() {
        return notifiedAt == null;
    }

    /**
     * 발송 성공을 기록해 신청을 소진시킨다 (MR-16).
     *
     * <p>이미 소진된 신청에 다시 찍지 않는다. 최초 발송 시각이 이 의사표시가 끝난
     * 시점이고, 덮어쓰면 언제 알렸는지를 잃는다 — {@code OccupancyParticipant.leave}와
     * 같은 규약이다.</p>
     *
     * @return 이번 호출로 소진됐으면 {@code true}, 이미 소진된 상태였으면 {@code false}
     */
    public boolean markNotified(OffsetDateTime notifiedAt) {
        if (this.notifiedAt != null) {
            return false;
        }
        this.notifiedAt = Objects.requireNonNull(notifiedAt, "발송 시각은 필수");
        return true;
    }
}
