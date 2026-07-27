package site.omagotchi.learningservice.space.domain;

import lombok.Getter;
import site.omagotchi.learningservice.space.domain.exception.InvalidSpaceCapacityException;
import site.omagotchi.learningservice.space.domain.exception.InvalidSpaceNameException;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * DB 기술에 의존하지 않는 공간 순수 도메인 객체.
 */
@Getter
public class Space {

    private static final int MAX_NAME_LENGTH = 50;

    private final Long id;
    private final Long cohortId;
    private final String name;
    private final SpaceType spaceType;
    private final Integer capacity;

    /**
     * 공간의 운영 상태.
     *
     * ACTIVE:
     * 신규 점유 또는 재실 신청 가능
     *
     * INACTIVE:
     * 신규 점유 또는 재실 신청 불가
     */
    private final SpaceOperationalStatus operationalStatus;

    /**
     * 공간의 비활성 사유.
     *
     * 비활성 사유 필수 여부가 아직 확정되지 않았으므로
     * null을 허용한다.
     */
    private final String inactiveReason;

    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final ZonedDateTime deletedAt;

    private Space(
            Long id,
            Long cohortId,
            String name,
            SpaceType spaceType,
            Integer capacity,
            SpaceOperationalStatus operationalStatus,
            String inactiveReason,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt,
            ZonedDateTime deletedAt
    ) {
        this.id = id;
        this.cohortId = cohortId;
        this.name = validateName(name);

        this.spaceType = Objects.requireNonNull(
                spaceType,
                "공간 유형은 필수입니다."
        );

        this.capacity = validateCapacity(capacity);

        this.operationalStatus = Objects.requireNonNull(
                operationalStatus,
                "공간 운영 상태는 필수입니다."
        );

        this.inactiveReason = normalizeInactiveReason(
                operationalStatus,
                inactiveReason
        );

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "생성 시각은 필수입니다."
        );

        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "수정 시각은 필수입니다."
        );

        this.deletedAt = deletedAt;
    }

    /**
     * 신규 공간을 생성한다.
     *
     * 신규 공간은 기본적으로 비활성 상태로 생성한다.
     */
    public static Space create(
            String name,
            SpaceType spaceType,
            Integer capacity,
            ZonedDateTime now
    ) {
        ZonedDateTime createdAt = Objects.requireNonNull(
                now,
                "생성 시각은 필수입니다."
        );

        return new Space(
                null,
                null,
                name,
                spaceType,
                capacity,
                SpaceOperationalStatus.INACTIVE,
                null,
                createdAt,
                createdAt,
                null
        );
    }

    /**
     * 영속성 계층에서 조회한 공간 데이터를
     * 도메인 객체로 복원한다.
     */
    public static Space restore(
            Long id,
            Long cohortId,
            String name,
            SpaceType spaceType,
            Integer capacity,
            SpaceOperationalStatus operationalStatus,
            String inactiveReason,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt,
            ZonedDateTime deletedAt
    ) {
        return new Space(
                Objects.requireNonNull(
                        id,
                        "공간 ID는 필수입니다."
                ),
                cohortId,
                name,
                spaceType,
                capacity,
                operationalStatus,
                inactiveReason,
                createdAt,
                updatedAt,
                deletedAt
        );
    }

    /**
     * 공간 이름을 변경한 새로운 객체를 반환한다.
     *
     * 이름 변경은 활성·비활성 상태와 관계없이 가능하다.
     */
    public Space changeName(
            String newName,
            ZonedDateTime updatedAt
    ) {
        return new Space(
                id,
                cohortId,
                newName,
                spaceType,
                capacity,
                operationalStatus,
                inactiveReason,
                createdAt,
                requireUpdatedAt(updatedAt),
                deletedAt
        );
    }

    /**
     * 공간 유형을 변경한 새로운 객체를 반환한다.
     *
     * 비활성 상태 여부와 실습실 활성 배정 존재 여부는
     * 추후 Application 계층에서 검증한다.
     */
    public Space changeType(
            SpaceType newType,
            ZonedDateTime updatedAt
    ) {
        return new Space(
                id,
                cohortId,
                name,
                newType,
                capacity,
                operationalStatus,
                inactiveReason,
                createdAt,
                requireUpdatedAt(updatedAt),
                deletedAt
        );
    }

    /**
     * 공간 최대 인원을 변경한 새로운 객체를 반환한다.
     *
     * 정원 축소 시 비활성 상태인지에 대한 검증은
     * 추후 Application 계층에서 수행한다.
     */
    public Space changeCapacity(
            Integer newCapacity,
            ZonedDateTime updatedAt
    ) {
        return new Space(
                id,
                cohortId,
                name,
                spaceType,
                newCapacity,
                operationalStatus,
                inactiveReason,
                createdAt,
                requireUpdatedAt(updatedAt),
                deletedAt
        );
    }

    /**
     * 공간을 활성화한 새로운 객체를 반환한다.
     *
     * 활성화되면 기존 비활성 사유는 제거한다.
     */
    public Space activate(ZonedDateTime updatedAt) {
        if (isActive()) {
            return this;
        }

        return new Space(
                id,
                cohortId,
                name,
                spaceType,
                capacity,
                SpaceOperationalStatus.ACTIVE,
                null,
                createdAt,
                requireUpdatedAt(updatedAt),
                deletedAt
        );
    }

    /**
     * 공간을 비활성화한 새로운 객체를 반환한다.
     *
     * 활성 점유 또는 재실자 존재 여부는
     * 추후 Application 계층에서 검증한다.
     */
    public Space deactivate(
            String reason,
            ZonedDateTime updatedAt
    ) {
        return new Space(
                id,
                cohortId,
                name,
                spaceType,
                capacity,
                SpaceOperationalStatus.INACTIVE,
                reason,
                createdAt,
                requireUpdatedAt(updatedAt),
                deletedAt
        );
    }

    /**
     * 공간을 소프트 삭제한 새로운 객체를 반환한다.
     *
     * 삭제는 비활성 상태에서만 가능하며,
     * 비활성 상태 검증과 활성 실습실 배정 검증은
     * 추후 Application 계층에서 수행한다.
     */
    public Space delete(ZonedDateTime deletedAt) {
        if (isDeleted()) {
            return this;
        }

        ZonedDateTime deletionTime = Objects.requireNonNull(
                deletedAt,
                "삭제 시각은 필수입니다."
        );

        return new Space(
                id,
                cohortId,
                name,
                spaceType,
                capacity,
                operationalStatus,
                inactiveReason,
                createdAt,
                deletionTime,
                deletionTime
        );
    }

    /**
     * 공간이 소프트 삭제되었는지 확인한다.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 공간이 활성 상태인지 확인한다.
     */
    public boolean isActive() {
        return !isDeleted()
                && operationalStatus == SpaceOperationalStatus.ACTIVE;
    }

    /**
     * 공간이 비활성 상태인지 확인한다.
     */
    public boolean isInactive() {
        return !isDeleted()
                && operationalStatus == SpaceOperationalStatus.INACTIVE;
    }

    /**
     * 공간이 회의실 유형인지 확인한다.
     */
    public boolean isMeetingRoom() {
        return spaceType == SpaceType.MEETING;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidSpaceNameException();
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new InvalidSpaceNameException();
        }

        return normalizedName;
    }

    private static Integer validateCapacity(Integer capacity) {
        if (capacity == null || capacity <= 0) {
            throw new InvalidSpaceCapacityException();
        }

        return capacity;
    }

    /**
     * 비활성 사유를 정규화한다.
     *
     * 활성 상태에서는 비활성 사유를 항상 null로 만든다.
     * 비활성 상태에서 null 또는 공백만 입력되면 null로 저장한다.
     */
    private static String normalizeInactiveReason(
            SpaceOperationalStatus operationalStatus,
            String inactiveReason
    ) {
        if (operationalStatus == SpaceOperationalStatus.ACTIVE) {
            return null;
        }

        if (inactiveReason == null || inactiveReason.isBlank()) {
            return null;
        }

        return inactiveReason.trim();
    }

    private static ZonedDateTime requireUpdatedAt(
            ZonedDateTime updatedAt
    ) {
        return Objects.requireNonNull(
                updatedAt,
                "수정 시각은 필수입니다."
        );
    }
}
