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
import java.util.UUID;

/**
 * 기수 참가 신청에 사용하는 가입 코드를 관리
 */
@Entity
@Table(name = "cohort_join_codes", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CohortJoinCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CohortJoinCodeStatus status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "issued_by_user_id", nullable = false)
    private UUID issuedByUserId;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    public static CohortJoinCode issue(
            Long cohortId,
            String codeHash,
            OffsetDateTime expiresAt,
            UUID issuedByUserId
    ) {
        if (expiresAt == null || !expiresAt.isAfter(OffsetDateTime.now())) {
            throw new IllegalArgumentException("가입 코드 만료 시각은 현재보다 이후여야 합니다.");
        }

        CohortJoinCode joinCode = new CohortJoinCode();
        joinCode.cohortId = cohortId;
        joinCode.codeHash = requireText(codeHash, "codeHash");
        joinCode.status = CohortJoinCodeStatus.ACTIVE;
        joinCode.expiresAt = expiresAt;
        joinCode.issuedByUserId = issuedByUserId;
        joinCode.issuedAt = OffsetDateTime.now();
        return joinCode;
    }

    public void revoke() {
        if (status != CohortJoinCodeStatus.ACTIVE) {
            return;
        }

        this.status = CohortJoinCodeStatus.REVOKED;
        this.revokedAt = OffsetDateTime.now();
    }

    public boolean isUsableAt(OffsetDateTime now) {
        return status == CohortJoinCodeStatus.ACTIVE && expiresAt.isAfter(now);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return value;
    }
}
