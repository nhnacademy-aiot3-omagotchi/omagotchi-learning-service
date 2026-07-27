package site.omagotchi.learningservice.space.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "spaces",
        schema = "learning_service"
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpaceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id")
    private Long cohortId;

    @Column(
            name = "name",
            nullable = false,
            length = 50
    )
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "type",
            nullable = false
    )
    private SpaceType type;

    @Column(
            name = "capacity",
            nullable = false
    )
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "operational_status",
            nullable = false
    )
    private SpaceOperationalStatus operationalStatus;

    @Column(
            name = "inactive_reason",
            length = 255
    )
    private String inactiveReason;

    @Column(
            name = "created_at",
            nullable = false
    )
    private OffsetDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public static SpaceJpaEntity from(
            Long id,
            Long cohortId,
            String name,
            SpaceType type,
            Integer capacity,
            SpaceOperationalStatus operationalStatus,
            String inactiveReason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt
    ) {
        return new SpaceJpaEntity(
                id,
                cohortId,
                name,
                type,
                capacity,
                operationalStatus,
                inactiveReason,
                createdAt,
                updatedAt,
                deletedAt
        );
    }
}
