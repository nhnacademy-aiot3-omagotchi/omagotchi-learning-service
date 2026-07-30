package site.omagotchi.learningservice.space.domain;

import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorType;
import site.omagotchi.learningservice.space.domain.exception.SpaceErrorCode;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SpaceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZonedDateTime CREATED_AT =
            ZonedDateTime.of(2026, 7, 24, 9, 0, 0, 0, SEOUL);
    private static final ZonedDateTime UPDATED_AT =
            CREATED_AT.plusHours(1);

    @Test
    void createsSpaceWithNormalizedNameAndRequestedAttributes() {
        Space space = Space.create(
                "  회의실 A  ",
                SpaceType.MEETING,
                8,
                42L,
                CREATED_AT
        );

        assertThat(space.getName()).isEqualTo("회의실 A");
        assertThat(space.getSpaceType()).isEqualTo(SpaceType.MEETING);
        assertThat(space.getCapacity()).isEqualTo(8);
        assertThat(space.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(space.isMeetingRoom()).isTrue();
        assertThat(space.isDeleted()).isFalse();
    }

    @Test
    void createsInactiveSpaceWithManagementCohort() {
        Space space = Space.create(
                "회의실 A",
                SpaceType.MEETING,
                8,
                42L,
                CREATED_AT
        );

        assertThat(space.getCohortId()).isEqualTo(42L);
        assertThat(space.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
    }

    @Test
    void restoresCohortIdFromPersistence() {
        Space space = spaceWithCohort();

        assertThat(space.getCohortId()).isEqualTo(42L);
    }

    @Test
    void preservesCohortIdAcrossAllStateChanges() {
        Space space = spaceWithCohort();

        assertThat(space.changeName("회의실 B", UPDATED_AT).getCohortId())
                .isEqualTo(42L);
        assertThat(space.changeType(SpaceType.LAB, UPDATED_AT).getCohortId())
                .isEqualTo(42L);
        assertThat(space.changeCapacity(12, UPDATED_AT).getCohortId())
                .isEqualTo(42L);
        assertThat(space.activate(UPDATED_AT).getCohortId())
                .isEqualTo(42L);
        assertThat(space.activate(UPDATED_AT)
                .deactivate("점검", UPDATED_AT.plusMinutes(1))
                .getCohortId())
                .isEqualTo(42L);
        assertThat(space.delete(UPDATED_AT).getCohortId())
                .isEqualTo(42L);
    }

    @Test
    void marksSpaceAsDeleted() {
        Space deleted = spaceWithCohort().delete(UPDATED_AT);

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isEqualTo(UPDATED_AT);
        assertThat(deleted.isActive()).isFalse();
        assertThat(deleted.isInactive()).isFalse();
    }

    @Test
    void activatesInactiveSpaceAndClearsReason() {
        Space inactive = Space.restore(
                1L,
                null,
                "회의실 A",
                SpaceType.MEETING,
                8,
                SpaceOperationalStatus.INACTIVE,
                "점검",
                CREATED_AT,
                CREATED_AT,
                null
        );

        Space activated = inactive.activate(UPDATED_AT);

        assertThat(activated.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.ACTIVE);
        assertThat(activated.getInactiveReason()).isNull();
        assertThat(activated.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void deactivatesActiveSpaceAndTrimsReason() {
        Space deactivated = activeSpace().deactivate(
                "  정기 점검  ",
                UPDATED_AT
        );

        assertThat(deactivated.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
        assertThat(deactivated.getInactiveReason()).isEqualTo("정기 점검");
        assertThat(deactivated.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void rejectsBlankDeactivationReason() {
        assertDomainError(
                SpaceErrorCode.INVALID_INACTIVE_REASON,
                () -> activeSpace().deactivate("   ", UPDATED_AT)
        );
    }

    @Test
    void rejectsCapacityReductionWhileActive() {
        assertDomainError(
                SpaceErrorCode.ACTIVE_CAPACITY_REDUCTION_NOT_ALLOWED,
                () -> activeSpace().changeCapacity(7, UPDATED_AT)
        );
    }

    @Test
    void allowsCapacityIncreaseWhileActive() {
        assertThat(activeSpace().changeCapacity(9, UPDATED_AT).getCapacity())
                .isEqualTo(9);
    }

    @Test
    void rejectsTypeChangeWhileActive() {
        assertDomainError(
                SpaceErrorCode.ACTIVE_TYPE_CHANGE_NOT_ALLOWED,
                () -> activeSpace().changeType(SpaceType.STUDY, UPDATED_AT)
        );
    }

    @Test
    void allowsSameTypeWhileActive() {
        assertThat(activeSpace()
                .changeType(SpaceType.MEETING, UPDATED_AT)
                .getSpaceType()).isEqualTo(SpaceType.MEETING);
    }

    @Test
    void rejectsDeletingActiveSpace() {
        assertDomainError(
                SpaceErrorCode.ACTIVE_SPACE_DELETE_NOT_ALLOWED,
                () -> activeSpace().delete(UPDATED_AT)
        );
    }

    @Test
    void rejectsDeletingUnmanagedInactiveSpaceWithConflictCode() {
        Space unmanagedSpace = Space.restore(
                1L,
                null,
                "관리 주체 없는 회의실",
                SpaceType.MEETING,
                8,
                SpaceOperationalStatus.INACTIVE,
                "운영 준비 중",
                CREATED_AT,
                CREATED_AT,
                null
        );

        assertDomainError(
                SpaceErrorCode.UNMANAGED_SPACE_DELETE_NOT_ALLOWED,
                () -> unmanagedSpace.delete(UPDATED_AT)
        );
        assertThat(SpaceErrorCode.UNMANAGED_SPACE_DELETE_NOT_ALLOWED.type())
                .isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    void deletesManagedInactiveNonLabSpace() {
        Space deleted = spaceWithCohort().delete(UPDATED_AT);

        assertThat(deleted.getDeletedAt()).isEqualTo(UPDATED_AT);
        assertThat(deleted.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void rejectsChangingAssignedLabTypeButAllowsDeletingIt() {
        Space assignedLab = Space.restore(
                1L,
                42L,
                "실습실 A",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.INACTIVE,
                "운영 준비 중",
                CREATED_AT,
                CREATED_AT,
                null
        );

        assertDomainError(
                SpaceErrorCode.ASSIGNED_LAB_TYPE_CHANGE_NOT_ALLOWED,
                () -> assignedLab.changeType(SpaceType.STUDY, UPDATED_AT)
        );
        assertThat(assignedLab.delete(UPDATED_AT).getDeletedAt())
                .isEqualTo(UPDATED_AT);
    }

    @Test
    void allowsUnassignedInactiveLabTypeChangeButNotDeletion() {
        Space unassignedLab = Space.restore(
                1L,
                null,
                "미배정 실습실",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.INACTIVE,
                "미배정",
                CREATED_AT,
                CREATED_AT,
                null
        );

        assertThat(unassignedLab.changeType(SpaceType.STUDY, UPDATED_AT)
                .getSpaceType()).isEqualTo(SpaceType.STUDY);
        assertDomainError(
                SpaceErrorCode.UNMANAGED_SPACE_DELETE_NOT_ALLOWED,
                () -> unassignedLab.delete(UPDATED_AT)
        );
    }

    @Test
    void rejectsNullNameWithSpaceErrorCode() {
        BusinessException exception = catchThrowableOfType(
                () -> Space.create(
                        null,
                        SpaceType.MEETING,
                        8,
                        42L,
                        CREATED_AT
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_NAME);
    }

    @Test
    void rejectsBlankNameWithSpaceErrorCode() {
        BusinessException exception = catchThrowableOfType(
                () -> Space.create(
                        "   ",
                        SpaceType.MEETING,
                        8,
                        42L,
                        CREATED_AT
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_NAME);
    }

    @Test
    void rejectsTooLongNameWithSpaceErrorCode() {
        BusinessException exception = catchThrowableOfType(
                () -> Space.create(
                        "가".repeat(51),
                        SpaceType.MEETING,
                        8,
                        42L,
                        CREATED_AT
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_NAME);
    }

    @Test
    void rejectsNullCapacityWithSpaceErrorCode() {
        BusinessException exception = catchThrowableOfType(
                () -> Space.create(
                        "회의실 A",
                        SpaceType.MEETING,
                        null,
                        42L,
                        CREATED_AT
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_CAPACITY);
    }

    @Test
    void rejectsZeroCapacityWithSpaceErrorCode() {
        BusinessException exception = catchThrowableOfType(
                () -> Space.create(
                        "회의실 A",
                        SpaceType.MEETING,
                        0,
                        42L,
                        CREATED_AT
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_CAPACITY);
    }

    @Test
    void rejectsNegativeCapacityWithSpaceErrorCode() {
        BusinessException exception = catchThrowableOfType(
                () -> Space.create(
                        "회의실 A",
                        SpaceType.MEETING,
                        -1,
                        42L,
                        CREATED_AT
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_CAPACITY);
    }

    @Test
    void rejectsNullTypeWithSpaceErrorCode() {
        BusinessException exception = catchThrowableOfType(
                () -> Space.create(
                        "회의실 A",
                        null,
                        8,
                        42L,
                        CREATED_AT
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_TYPE);
    }

    @Test
    void rejectsNullManagementCohortId() {
        BusinessException exception = catchThrowableOfType(
                () -> Space.create(
                        "회의실 A",
                        SpaceType.MEETING,
                        8,
                        null,
                        CREATED_AT
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_COHORT_ID);
    }

    @Test
    void assignsAndUnassignsLabCohort() {
        Space lab = Space.restore(
                1L,
                null,
                "실습실 A",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.INACTIVE,
                null,
                CREATED_AT,
                CREATED_AT,
                null
        );

        Space assigned = lab.assignCohort(42L, UPDATED_AT);
        Space unassigned = assigned.unassignCohort(UPDATED_AT.plusHours(1));

        assertThat(assigned.getCohortId()).isEqualTo(42L);
        assertThat(assigned.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(unassigned.getCohortId()).isNull();
        assertThat(unassigned.getUpdatedAt())
                .isEqualTo(UPDATED_AT.plusHours(1));
    }

    @Test
    void rejectsCohortAssignmentForNonLab() {
        BusinessException exception = catchThrowableOfType(
                () -> spaceWithCohort().assignCohort(42L, UPDATED_AT),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.LAB_ONLY_COHORT_ASSIGNMENT);
    }

    @Test
    void rejectsDuplicateAssignmentAndUnassignedRelease() {
        BusinessException duplicate = catchThrowableOfType(
                () -> assignedLab().assignCohort(42L, UPDATED_AT),
                BusinessException.class
        );
        Space unassignedLab = Space.restore(
                1L,
                null,
                "실습실 A",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.INACTIVE,
                null,
                CREATED_AT,
                CREATED_AT,
                null
        );
        BusinessException notAssigned = catchThrowableOfType(
                () -> unassignedLab.unassignCohort(UPDATED_AT),
                BusinessException.class
        );

        assertThat(duplicate.getErrorCode())
                .isEqualTo(SpaceErrorCode.LAB_ALREADY_ASSIGNED);
        assertThat(notAssigned.getErrorCode())
                .isEqualTo(SpaceErrorCode.LAB_NOT_ASSIGNED);
    }

    private Space assignedLab() {
        return Space.restore(
                1L,
                42L,
                "실습실 A",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.INACTIVE,
                null,
                CREATED_AT,
                CREATED_AT,
                null
        );
    }

    private Space spaceWithCohort() {
        return Space.restore(
                1L,
                42L,
                "회의실 A",
                SpaceType.MEETING,
                8,
                SpaceOperationalStatus.INACTIVE,
                null,
                CREATED_AT,
                CREATED_AT,
                null
        );
    }

    private Space activeSpace() {
        return Space.restore(
                1L,
                null,
                "회의실 A",
                SpaceType.MEETING,
                8,
                SpaceOperationalStatus.ACTIVE,
                null,
                CREATED_AT,
                CREATED_AT,
                null
        );
    }

    private void assertDomainError(
            SpaceErrorCode expected,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action
    ) {
        BusinessException exception = catchThrowableOfType(
                action,
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(expected);
    }
}
