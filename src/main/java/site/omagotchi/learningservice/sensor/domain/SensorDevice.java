package site.omagotchi.learningservice.sensor.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sensor_devices", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorDevice {
    private static final int MAX_DEVICE_EUI_LENGTH = 32;
    private static final int MAX_MODEL_LENGTH = 32;
    private static final int MAX_DISPLAY_NAME_LENGTH = 64;
    private static final int MAX_INSTALLATION_POINT_LENGTH = 64;

    @Id
    @Column(name = "device_eui", length = MAX_DEVICE_EUI_LENGTH)
    private String deviceEui;

    @Column(name = "space_id")
    private Long spaceId;

    @Column(name = "model", nullable = false, length = MAX_MODEL_LENGTH)
    private String model;

    @Column(name = "display_name", length = MAX_DISPLAY_NAME_LENGTH)
    private String displayName;

    @Column(name = "installation_point", length = MAX_INSTALLATION_POINT_LENGTH)
    private String installationPoint;

    @Column(name = "expected_interval_seconds", nullable = false)
    private Integer expectedIntervalSeconds;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "installed_at")
    private Instant installedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /// 센서 생성
    public static SensorDevice create(
            String deviceEui,
            Long spaceId,
            String model,
            String displayName,
            String installationPoint,
            Integer expectedIntervalSeconds,
            Instant installedAt
    ) {
        SensorDevice device = new SensorDevice();
        device.deviceEui = normalizeDeviceEui(deviceEui);
        device.spaceId = spaceId;
        device.model = requireText(model, "model", MAX_MODEL_LENGTH);
        device.displayName = requireText(displayName, "displayName", MAX_DISPLAY_NAME_LENGTH);
        device.installationPoint = optionalText(installationPoint, "installationPoint", MAX_INSTALLATION_POINT_LENGTH);
        device.expectedIntervalSeconds = requiredInterval(expectedIntervalSeconds);
        // 컬럼 DEFAULT 는 JPA 가 null 을 명시적으로 INSERT 하므로 적용되지 않는다.
        // 회수된 상태로 등록할 이유가 없으므로 생성 시에는 항상 true 다.
        device.active = true;
        device.installedAt = installedAt;

        return device;
    }

    /// 센서 정보 변경
    public void update(
            Long spaceId,
            String displayName,
            String installationPoint,
            Integer expectedIntervalSeconds,
            Instant installedAt) {

        this.spaceId = spaceId;
        this.displayName = optionalText(displayName, "displayName", MAX_DISPLAY_NAME_LENGTH);
        this.installationPoint = optionalText(installationPoint, "installationPoint", MAX_INSTALLATION_POINT_LENGTH);
        this.expectedIntervalSeconds = requiredInterval(expectedIntervalSeconds);
        this.installedAt = installedAt;
    }

    /// 센서 제거 or 비활성화 -> 상태 변경 active=false
    public void changeActive(boolean active) {
        this.active = active;
    }

    // 제약 조건 검증 헬퍼메서드
    private static Integer requiredInterval(Integer seconds) {
        if (Objects.isNull(seconds)) {
            throw new IllegalArgumentException("expectedIntervalSeconds가 null입니다.");
        }

        if (seconds <= 0) {
            throw new IllegalArgumentException("expectedIntervalSeconds는 1초 이상이여야 합니다.");
        }

        return seconds;
    }

    private static String normalizeDeviceEui(String deviceEui) {
        String normalized = requireText(deviceEui, "deviceEui", MAX_DEVICE_EUI_LENGTH);

        if (!normalized.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException("장치 EUI는 16진수여야 합니다.");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은(는) %d자를 넘을 수 없습니다.".formatted(maxLength));
        }

        return normalized;
    }

    private static String optionalText(String value, String fieldName, int maxLength) {
        if (Objects.isNull(value) || value.isBlank()) {
            return null;
        }

        return requireText(value, fieldName, maxLength);
    }

}
