package site.omagotchi.learningservice.space.domain;

import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * DB 기술에 의존하지 않는 공간 순수 도메인 객체.
 */
@Getter
public class Space {

    private final Long id;
    private final Long cohortId;
    private final String name;
    private final SpaceType spaceType;
    private final Integer capacity;

    /**
     * 공간의 운영 상태.
     * <p>
     * ACTIVE:
     * 신규 점유 또는 재실 신청 가능
     * <p>
     * INACTIVE:
     * 신규 점유 또는 재실 신청 불가
     */
    private final SpaceOperationalStatus operationalStatus;

    /**
     * 공간의 비활성 사유.
     * <p>
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
        SpaceAttributes attributes = new SpaceAttributes(
                name,
                spaceType,
                capacity
        );
        this.id = id;
        this.cohortId = cohortId;
        this.name = attributes.name();
        this.spaceType = attributes.spaceType();
        this.capacity = attributes.capacity();

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
     * <p>
     * 신규 공간은 기본적으로 비활성 상태로 생성한다.
     */
    public static Space create(
            String name,
            SpaceType spaceType,
            Integer capacity,
            Long cohortId,
            ZonedDateTime now
    ) {
        ZonedDateTime createdAt = Objects.requireNonNull(
                now,
                "생성 시각은 필수입니다."
        );

        return new Space(
                null,
                validateCohortId(cohortId),
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
     * <p>
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
     * <p>
     * 실제 유형 변경은 비활성 공간에서만 가능하며,
     * 기수에 배정된 실습실은 유형을 변경할 수 없다.
     */
    public Space changeType(
            SpaceType newType,
            ZonedDateTime updatedAt
    ) {
        ensureNotDeleted();

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
     * <p>
     * 활성 공간은 정원을 늘리거나 유지할 수 있지만
     * 축소할 수 없다.
     */
    public Space changeCapacity(
            Integer newCapacity,
            ZonedDateTime updatedAt
    ) {
        ensureNotDeleted();

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
     * <p>
     * 활성화되면 기존 비활성 사유는 제거한다.
     */
    public Space activate(ZonedDateTime updatedAt) {
        ensureNotDeleted();

        if (isActive()) {
            throw new IllegalStateException("이미 활성화된 공간입니다.");
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
     * <p>
     * 활성 점유 여부처럼 외부 조회가 필요한 조건은
     * Application 계층에서 검증한다.
     */
    public Space deactivate(
            String reason,
            ZonedDateTime updatedAt
    ) {
        ensureNotDeleted();

        if (isInactive()) {
            throw new IllegalStateException("이미 비활성화된 공간입니다.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "비활성 사유는 필수입니다."
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
     * <p>
     * 삭제는 비활성 상태이며 관리 주체 기수가 있는 공간에만 가능하다.
     */
    public Space delete(ZonedDateTime deletedAt) {
        if (isDeleted()) {
            throw new IllegalStateException("이미 삭제된 공간입니다.");
        }

        if (isActive()) {
            throw new IllegalStateException(
                    "활성 공간은 삭제할 수 없습니다."
            );
        }

        if (cohortId == null) {
            throw new IllegalStateException(
                    "관리 주체가 없는 공간은 삭제할 수 없습니다."
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

    public Space assignCohort(
            Long cohortId,
            ZonedDateTime updatedAt
    ) {
        ensureNotDeleted();
        ensureLab();

        if (this.cohortId != null) {
            throw new IllegalStateException("이미 기수에 배정된 실습실입니다.");
        }

        return new Space(
                id,
                validateCohortId(cohortId),
                name,
                spaceType,
                capacity,
                operationalStatus,
                inactiveReason,
                createdAt,
                requireUpdatedAt(updatedAt),
                deletedAt
        );
    }

    public Space unassignCohort(ZonedDateTime updatedAt) {
        ensureNotDeleted();
        ensureLab();

        if (cohortId == null) {
            throw new IllegalStateException("기수에 배정되지 않은 실습실입니다.");
        }

        return new Space(
                id,
                null,
                name,
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

    private void ensureLab() {
        if (spaceType != SpaceType.LAB) {
            throw new IllegalStateException(
                    "실습실에만 기수를 배정하거나 해제할 수 있습니다."
            );
        }
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new IllegalStateException("삭제된 공간은 변경할 수 없습니다.");
        }
    }

    private static Long validateCohortId(Long cohortId) {
        if (cohortId == null || cohortId <= 0) {
            throw new IllegalArgumentException("기수 ID는 양수여야 합니다.");
        }

        return cohortId;
    }

    /**
     * 비활성 사유를 정규화한다.
     * <p>
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
