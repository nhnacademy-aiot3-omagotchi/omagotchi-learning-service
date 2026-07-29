package site.omagotchi.learningservice.space.domain;

import lombok.Getter;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.domain.exception.SpaceErrorCode;

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
     * 생성 직후의 비활성 공간은 사유가 없을 수 있지만,
     * 명시적인 비활성화 요청에는 사유가 필수다.
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

        this.spaceType = validateType(spaceType);

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
        ensureNotDeleted();

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
     * 실제 유형 변경은 비활성 공간에서만 가능하며,
     * 기수에 배정된 실습실은 유형을 변경할 수 없다.
     */
    public Space changeType(
            SpaceType newType,
            ZonedDateTime updatedAt
    ) {
        ensureNotDeleted();

        if (spaceType != newType && isActive()) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_TYPE_CHANGE_NOT_ALLOWED
            );
        }

        if (spaceType != newType && isAssignedLab()) {
            throw new BusinessException(
                    SpaceErrorCode.ASSIGNED_LAB_TYPE_CHANGE_NOT_ALLOWED
            );
        }

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
     * 활성 공간은 정원을 늘리거나 유지할 수 있지만
     * 축소할 수 없다.
     */
    public Space changeCapacity(
            Integer newCapacity,
            ZonedDateTime updatedAt
    ) {
        ensureNotDeleted();

        if (newCapacity != null
                && newCapacity < capacity
                && isActive()) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_CAPACITY_REDUCTION_NOT_ALLOWED
            );
        }

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
        ensureNotDeleted();

        if (isActive()) {
            throw new BusinessException(SpaceErrorCode.ALREADY_ACTIVE);
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
     * 활성 점유 여부처럼 외부 조회가 필요한 조건은
     * Application 계층에서 검증한다.
     */
    public Space deactivate(
            String reason,
            ZonedDateTime updatedAt
    ) {
        ensureNotDeleted();

        if (isInactive()) {
            throw new BusinessException(SpaceErrorCode.ALREADY_INACTIVE);
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                    SpaceErrorCode.INVALID_INACTIVE_REASON
            );
        }

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
     * 삭제는 비활성 상태에서만 가능하며 기수에 배정된
     * 실습실은 삭제할 수 없다. 활성 점유 여부는
     * Application 계층에서 검증한다.
     */
    public Space delete(ZonedDateTime deletedAt) {
        if (isDeleted()) {
            throw new BusinessException(SpaceErrorCode.DELETED_SPACE);
        }

        if (isActive()) {
            throw new BusinessException(
                    SpaceErrorCode.ACTIVE_SPACE_DELETE_NOT_ALLOWED
            );
        }

        if (cohortId == null) {
            throw new BusinessException(
                    SpaceErrorCode.UNMANAGED_SPACE_DELETE_NOT_ALLOWED
            );
        }

        if (isAssignedLab()) {
            throw new BusinessException(
                    SpaceErrorCode.ASSIGNED_LAB_DELETE_NOT_ALLOWED
            );
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

    public boolean isAssignedLab() {
        return spaceType == SpaceType.LAB && cohortId != null;
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new BusinessException(SpaceErrorCode.DELETED_SPACE);
        }
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(SpaceErrorCode.INVALID_NAME);
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(SpaceErrorCode.INVALID_NAME);
        }

        return normalizedName;
    }

    private static Integer validateCapacity(Integer capacity) {
        if (capacity == null || capacity <= 0) {
            throw new BusinessException(SpaceErrorCode.INVALID_CAPACITY);
        }

        return capacity;
    }

    private static SpaceType validateType(SpaceType spaceType) {
        if (spaceType == null) {
            throw new BusinessException(SpaceErrorCode.INVALID_TYPE);
        }

        return spaceType;
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
