package site.omagotchi.learningservice.rule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
