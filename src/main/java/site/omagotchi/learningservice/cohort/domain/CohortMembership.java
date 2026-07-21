package site.omagotchi.learningservice.cohort.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 사용자가 특정 기수에 어떤 역할과 상태로 소속되어 있는지 관리한다.
 * 승인, 거절, 종료 같은 상태 전이는 version 없이 status 조건부 업데이트로 처리한다.
 */
@Getter
@Entity
@Table(name = "cohort_memberships", schema = "learning_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CohortMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CohortMembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CohortMembershipStatus status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "processed_by_user_id")
    private Long processedByUserId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    /**
     * pending: 보류중
     */
    public static CohortMembership pending(Long cohortId, Long userId, CohortMembershipRole role) {
        CohortMembership membership = new CohortMembership();
        membership.cohortId = cohortId;
        membership.userId = userId;
        membership.role = role;
        membership.status = CohortMembershipStatus.PENDING;
        membership.requestedAt = OffsetDateTime.now();
        return membership;
    }

    /**
     * 활성화 매니저
     */
    public static CohortMembership activeManager(Long cohortId, Long userId, Long
            processedByUserId) {
        CohortMembership membership = new CohortMembership();
        membership.cohortId = cohortId;
        membership.userId = userId;
        membership.role = CohortMembershipRole.MANAGER;
        membership.status = CohortMembershipStatus.ACTIVE;
        membership.requestedAt = OffsetDateTime.now();
        membership.processedAt = OffsetDateTime.now();
        membership.processedByUserId = processedByUserId;
        return membership;
    }
}
