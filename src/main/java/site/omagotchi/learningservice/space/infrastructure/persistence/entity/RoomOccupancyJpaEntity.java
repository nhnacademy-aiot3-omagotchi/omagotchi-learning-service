package site.omagotchi.learningservice.space.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "room_occupancies",
        schema = "learning_service"
)@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomOccupancyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "space_id",
            nullable = false
    )
    private Long spaceId;

    @Column(
            name = "status",
            nullable = false
    )
    private String status;

    @Column(
            name = "started_at",
            nullable = false
    )
    private OffsetDateTime startedAt;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private OffsetDateTime expiresAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;
}