package site.omagotchi.learningservice.space.domain;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(space.getCohortId()).isEqualTo(42L);
        assertThat(space.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(space.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
        assertThat(space.isMeetingRoom()).isTrue();
        assertThat(space.isDeleted()).isFalse();
    }

    @Test
    void preservesCohortIdAcrossStateChanges() {
        Space space = managedSpace();

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
                .getCohortId()).isEqualTo(42L);
        assertThat(space.delete(UPDATED_AT).getCohortId()).isEqualTo(42L);
    }

    @Test
    void activatesAndDeactivatesSpace() {
        Space inactive = Space.restore(
                1L,
                42L,
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
        Space deactivated = activated.deactivate(
                "  정기 점검  ",
                UPDATED_AT.plusMinutes(1)
        );

        assertThat(activated.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.ACTIVE);
        assertThat(activated.getInactiveReason()).isNull();
        assertThat(deactivated.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
        assertThat(deactivated.getInactiveReason()).isEqualTo("정기 점검");
    }

    @Test
    void marksManagedInactiveSpaceAsDeleted() {
        Space deleted = managedSpace().delete(UPDATED_AT);

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isEqualTo(UPDATED_AT);
        assertThat(deleted.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(deleted.isActive()).isFalse();
        assertThat(deleted.isInactive()).isFalse();
    }

    @Test
    void rejectsInvalidAttributesOnCreation() {
        String tooLongName = "가".repeat(51);

        assertThatThrownBy(() -> Space.create(
                null, SpaceType.MEETING, 8, 42L, CREATED_AT
        )).isInstanceOf(SpaceValidationException.class);
        assertThatThrownBy(() -> Space.create(
                "   ", SpaceType.MEETING, 8, 42L, CREATED_AT
        )).isInstanceOf(SpaceValidationException.class);
        assertThatThrownBy(() -> Space.create(
                tooLongName, SpaceType.MEETING, 8, 42L, CREATED_AT
        )).isInstanceOf(SpaceValidationException.class);
        assertThatThrownBy(() -> Space.create(
                "회의실 A", null, 8, 42L, CREATED_AT
        )).isInstanceOf(SpaceValidationException.class);
        assertThatThrownBy(() -> Space.create(
                "회의실 A", SpaceType.MEETING, null, 42L, CREATED_AT
        )).isInstanceOf(SpaceValidationException.class);
        assertThatThrownBy(() -> Space.create(
                "회의실 A", SpaceType.MEETING, 0, 42L, CREATED_AT
        )).isInstanceOf(SpaceValidationException.class);
        assertThatThrownBy(() -> Space.create(
                "회의실 A", SpaceType.MEETING, -1, 42L, CREATED_AT
        )).isInstanceOf(SpaceValidationException.class);
    }

    @Test
    void rejectsInvalidCohortIdOnCreation() {
        assertThatThrownBy(() -> Space.create(
                "회의실 A", SpaceType.MEETING, 8, null, CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(SpaceValidationException.class);
    }

    @Test
    void protectsStateTransitionInvariants() {
        Space active = activeSpace();
        Space managed = managedSpace();

        assertThatThrownBy(() -> active.changeCapacity(7, UPDATED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> active.changeType(
                SpaceType.STUDY,
                UPDATED_AT
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> active.delete(UPDATED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> active.activate(UPDATED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> managed.deactivate(
                "점검",
                UPDATED_AT
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> active.deactivate(
                "   ",
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesActiveSpaceChangePolicies() {
        assertThat(activeSpace().changeCapacity(9, UPDATED_AT).getCapacity())
                .isEqualTo(9);
        assertThat(activeSpace().changeType(
                SpaceType.MEETING,
                UPDATED_AT
        ).getSpaceType()).isEqualTo(SpaceType.MEETING);
    }

    @Test
    void protectsManagementAndAssignedLabPolicies() {
        Space unmanagedLab = lab(null);
        Space assignedLab = lab(42L);

        assertThat(unmanagedLab.changeType(SpaceType.STUDY, UPDATED_AT)
                .getSpaceType()).isEqualTo(SpaceType.STUDY);
        assertThat(assignedLab.delete(UPDATED_AT).isDeleted()).isTrue();
        assertThatThrownBy(() -> unmanagedLab.delete(UPDATED_AT))
                .isInstanceOf(IllegalStateException.class);

        // 배정 여부는 유형 변경의 게이팅 조건이 아니다 — RM-22 미수용, RM-14(비활성 상태)로 대체
        assertThat(assignedLab.changeType(SpaceType.STUDY, UPDATED_AT)
                .getSpaceType()).isEqualTo(SpaceType.STUDY);
    }

    @Test
    void assignsAndUnassignsLabCohort() {
        Space assigned = lab(null).assignCohort(42L, UPDATED_AT);
        Space unassigned = assigned.unassignCohort(UPDATED_AT.plusHours(1));

        assertThat(assigned.getCohortId()).isEqualTo(42L);
        assertThat(assigned.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(unassigned.getCohortId()).isNull();
        assertThat(unassigned.getUpdatedAt())
                .isEqualTo(UPDATED_AT.plusHours(1));
    }

    @Test
    void protectsLabAssignmentInvariants() {
        Space managed = managedSpace();
        Space assignedLab = lab(42L);
        Space unmanagedLab = lab(null);

        assertThatThrownBy(() -> managed.assignCohort(42L, UPDATED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> assignedLab.assignCohort(43L, UPDATED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> unmanagedLab.unassignCohort(UPDATED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    private Space managedSpace() {
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
                42L,
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

    private Space lab(Long cohortId) {
        return Space.restore(
                1L,
                cohortId,
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
}
