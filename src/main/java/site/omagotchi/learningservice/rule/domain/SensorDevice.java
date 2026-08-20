package site.omagotchi.learningservice.rule.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sensor_devices", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorDevice {

    @Id
    @Column(name = "device_eui", length = 32)
    private String deviceEui;

    @Column(name = "space_id")
    private Long spaceId;

    @Column(name = "model", nullable = false, length = 32)
    private String model;

    @Column(name = "display_name", length = 64)
    private String displayName;

    @Column(name = "installation_point", length = 64)
    private String installationPoint;

    @Column(name = "expected_interval_seconds", nullable = false)
    private Integer expectedIntervalSeconds;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "installed_at")
    private OffsetDateTime installedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onPersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
