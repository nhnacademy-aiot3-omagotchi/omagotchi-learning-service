package site.omagotchi.learningservice.cohort.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 교육 기수의 기본 정보와 운영 상태를 관리
 */
@Entity
@Table(name = "cohorts", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cohort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CohortStatus status;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    public static Cohort create(
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            UUID createdByUserId
    ) {
        validatePeriod(startDate, endDate);

        Cohort cohort = new Cohort();
        cohort.name = requireText(name, "name");
        cohort.description = description;
        cohort.startDate = startDate;
        cohort.endDate = endDate;
        cohort.status = CohortStatus.PREPARING;
        cohort.createdByUserId = createdByUserId;
        return cohort;
    }

    public void updateBasicInfo(String name, String description, LocalDate startDate, LocalDate endDate) {
        if (status == CohortStatus.CLOSED) {
            throw new IllegalStateException("종료된 기수는 수정할 수 없습니다.");
        }
        validatePeriod(startDate, endDate);

        this.name = requireText(name, "name");
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void activate(boolean hasActiveManager) {
        if (status != CohortStatus.PREPARING) {
            throw new IllegalStateException("준비 상태의 기수만 운영 상태로 전환할 수 있습니다.");
        }
        if (!hasActiveManager) {
            throw new IllegalStateException("활성 관리자 소속이 있어야 기수를 운영할 수 있습니다.");
        }

        this.status = CohortStatus.ACTIVE;
    }

    public void close() {
        if (status != CohortStatus.ACTIVE) {
            throw new IllegalStateException("운영 상태의 기수만 종료할 수 있습니다.");
        }

        this.status = CohortStatus.CLOSED;
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("기수 시작일과 종료일은 필수입니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("기수 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return value;
    }
}
