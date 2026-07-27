package site.omagotchi.learningservice.space.domain;

import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.domain.exception.InvalidSpaceCapacityException;
import site.omagotchi.learningservice.space.domain.exception.InvalidSpaceNameException;
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
    void createsInactiveSpaceWithoutCohort() {
        Space space = Space.create(
                "회의실 A",
                SpaceType.MEETING,
                8,
                CREATED_AT
        );

        assertThat(space.getCohortId()).isNull();
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
        assertThat(space.deactivate("점검", UPDATED_AT).getCohortId())
                .isEqualTo(42L);
        assertThat(space.delete(UPDATED_AT).getCohortId())
                .isEqualTo(42L);
    }

    @Test
    void rejectsNullNameWithSpaceErrorCode() {
        InvalidSpaceNameException exception = catchThrowableOfType(
                () -> Space.create(
                        null,
                        SpaceType.MEETING,
                        8,
                        CREATED_AT
                ),
                InvalidSpaceNameException.class
        );

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_NAME);
    }

    @Test
    void rejectsBlankNameWithSpaceErrorCode() {
        InvalidSpaceNameException exception = catchThrowableOfType(
                () -> Space.create(
                        "   ",
                        SpaceType.MEETING,
                        8,
                        CREATED_AT
                ),
                InvalidSpaceNameException.class
        );

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_NAME);
    }

    @Test
    void rejectsTooLongNameWithSpaceErrorCode() {
        InvalidSpaceNameException exception = catchThrowableOfType(
                () -> Space.create(
                        "가".repeat(51),
                        SpaceType.MEETING,
                        8,
                        CREATED_AT
                ),
                InvalidSpaceNameException.class
        );

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_NAME);
    }

    @Test
    void rejectsNullCapacityWithSpaceErrorCode() {
        InvalidSpaceCapacityException exception = catchThrowableOfType(
                () -> Space.create(
                        "회의실 A",
                        SpaceType.MEETING,
                        null,
                        CREATED_AT
                ),
                InvalidSpaceCapacityException.class
        );

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_CAPACITY);
    }

    @Test
    void rejectsZeroCapacityWithSpaceErrorCode() {
        InvalidSpaceCapacityException exception = catchThrowableOfType(
                () -> Space.create(
                        "회의실 A",
                        SpaceType.MEETING,
                        0,
                        CREATED_AT
                ),
                InvalidSpaceCapacityException.class
        );

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_CAPACITY);
    }

    @Test
    void rejectsNegativeCapacityWithSpaceErrorCode() {
        InvalidSpaceCapacityException exception = catchThrowableOfType(
                () -> Space.create(
                        "회의실 A",
                        SpaceType.MEETING,
                        -1,
                        CREATED_AT
                ),
                InvalidSpaceCapacityException.class
        );

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode())
                .isEqualTo(SpaceErrorCode.INVALID_CAPACITY);
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
}
