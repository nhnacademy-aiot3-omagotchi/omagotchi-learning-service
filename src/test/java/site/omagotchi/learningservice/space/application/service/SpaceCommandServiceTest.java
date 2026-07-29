package site.omagotchi.learningservice.space.application.service;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.out.SpaceRepository;
import site.omagotchi.learningservice.space.application.port.out.SpaceOccupancyQueryPort;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.exception.SpaceErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceCommandServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceOccupancyQueryPort spaceOccupancyQueryPort;

    private SpaceCommandService spaceCommandService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, SEOUL);
        spaceCommandService = new SpaceCommandService(
                spaceRepository,
                spaceOccupancyQueryPort,
                clock
        );
    }

    @Test
    void rejectsDuplicateNameOnCreate() {
        when(spaceRepository.existsActiveByName("회의실 A"))
                .thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.DUPLICATE_NAME,
                () -> spaceCommandService.create(
                        new CreateSpaceCommand(
                                " 회의실 A ",
                                SpaceType.MEETING,
                                8
                        )
                )
        );
    }

    @Test
    void rejectsDuplicateNameDifferingOnlyByCase() {
        when(spaceRepository.existsActiveByName("회의실 a"))
                .thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.DUPLICATE_NAME,
                () -> spaceCommandService.create(
                        new CreateSpaceCommand(
                                "회의실 a",
                                SpaceType.MEETING,
                                8
                        )
                )
        );
    }

    @Test
    void allowsNameUsedOnlyBySoftDeletedSpace() {
        when(spaceRepository.existsActiveByName("회의실 A"))
                .thenReturn(false);
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space created = spaceCommandService.create(
                new CreateSpaceCommand(
                        " 회의실 A ",
                        SpaceType.MEETING,
                        8
                )
        );

        assertThat(created.getName()).isEqualTo("회의실 A");
    }

    @Test
    void rejectsDuplicateNameOnUpdate() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceRepository.existsActiveByNameAndIdNot("회의실 B", 1L))
                .thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.DUPLICATE_NAME,
                () -> spaceCommandService.update(
                        1L,
                        new UpdateSpaceCommand(
                                " 회의실 B ",
                                SpaceType.MEETING,
                                8
                        )
                )
        );
    }

    @Test
    void rejectsUpdatingMissingSpace() {
        when(spaceRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertBusinessError(
                SpaceErrorCode.NOT_FOUND,
                () -> spaceCommandService.update(
                        999L,
                        new UpdateSpaceCommand(
                                "회의실 B",
                                SpaceType.MEETING,
                                8
                        )
                )
        );
    }

    @Test
    void rejectsDeletingMissingSpace() {
        when(spaceRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertBusinessError(
                SpaceErrorCode.NOT_FOUND,
                () -> spaceCommandService.delete(999L)
        );
    }

    @Test
    void rejectsActivatingMissingSpace() {
        when(spaceRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertBusinessError(
                SpaceErrorCode.NOT_FOUND,
                () -> spaceCommandService.activate(999L)
        );
    }

    @Test
    void rejectsDeactivatingMissingSpace() {
        when(spaceRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertBusinessError(
                SpaceErrorCode.NOT_FOUND,
                () -> spaceCommandService.deactivate(999L, "점검")
        );
    }

    @Test
    void createsSpaceWithoutChangingExistingBehavior() {
        when(spaceRepository.existsActiveByName("회의실 A"))
                .thenReturn(false);
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space created = spaceCommandService.create(
                new CreateSpaceCommand(
                        " 회의실 A ",
                        SpaceType.MEETING,
                        8
                )
        );

        assertThat(created.getName()).isEqualTo("회의실 A");
        assertThat(created.getCohortId()).isNull();
        assertThat(created.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
    }

    @Test
    void updatesSpaceWithoutChangingExistingBehavior() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceRepository.existsActiveByNameAndIdNot("회의실 B", 1L))
                .thenReturn(false);
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space updated = spaceCommandService.update(
                1L,
                new UpdateSpaceCommand(
                        "회의실 B",
                        SpaceType.STUDY,
                        12
                )
        );

        assertThat(updated.getName()).isEqualTo("회의실 B");
        assertThat(updated.getSpaceType()).isEqualTo(SpaceType.STUDY);
        assertThat(updated.getCapacity()).isEqualTo(12);
        assertThat(updated.getCohortId()).isEqualTo(42L);
    }

    @Test
    void deletesSpaceWithoutChangingExistingBehavior() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);

        spaceCommandService.delete(1L);

        verify(spaceRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getCohortId()).isEqualTo(42L);
    }

    @Test
    void activatesInactiveSpaceAndClearsInactiveReason() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.INACTIVE,
                        " 정기 점검 ",
                        null
                )));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space activated = spaceCommandService.activate(1L);

        assertThat(activated.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.ACTIVE);
        assertThat(activated.getInactiveReason()).isNull();
        assertThat(activated.getUpdatedAt())
                .isEqualTo(ZonedDateTime.ofInstant(NOW, SEOUL));
    }

    @Test
    void rejectsActivatingAlreadyActiveSpace() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        null
                )));

        assertBusinessError(
                SpaceErrorCode.ALREADY_ACTIVE,
                () -> spaceCommandService.activate(1L)
        );
    }

    @Test
    void rejectsActivatingDeletedSpace() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.INACTIVE,
                        null,
                        ZonedDateTime.ofInstant(NOW, SEOUL)
                )));

        assertBusinessError(
                SpaceErrorCode.DELETED_SPACE,
                () -> spaceCommandService.activate(1L)
        );
    }

    @Test
    void rejectsDeactivatingDeletedSpace() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.INACTIVE,
                        null,
                        ZonedDateTime.ofInstant(NOW, SEOUL)
                )));

        assertBusinessError(
                SpaceErrorCode.DELETED_SPACE,
                () -> spaceCommandService.deactivate(1L, "점검")
        );
    }

    @Test
    void rejectsDeletingDeletedSpace() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.INACTIVE,
                        null,
                        ZonedDateTime.ofInstant(NOW, SEOUL)
                )));

        assertBusinessError(
                SpaceErrorCode.DELETED_SPACE,
                () -> spaceCommandService.delete(1L)
        );
    }

    @Test
    void deactivatesActiveSpaceWithNormalizedReason() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        null
                )));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space deactivated = spaceCommandService.deactivate(
                1L,
                "  냉방 점검  "
        );

        assertThat(deactivated.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
        assertThat(deactivated.getInactiveReason()).isEqualTo("냉방 점검");
    }

    @Test
    void rejectsDeactivatingAlreadyInactiveSpace() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(existingSpace()));

        assertBusinessError(
                SpaceErrorCode.ALREADY_INACTIVE,
                () -> spaceCommandService.deactivate(1L, "점검")
        );
    }

    @Test
    void rejectsBlankInactiveReason() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        null
                )));

        assertBusinessError(
                SpaceErrorCode.INVALID_INACTIVE_REASON,
                () -> spaceCommandService.deactivate(1L, "   ")
        );
    }

    @Test
    void rejectsDeactivationWhenActiveOccupancyExists() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        null
                )));
        when(spaceOccupancyQueryPort.existsActiveOccupancy(
                any(Long.class),
                any(ZonedDateTime.class)
        )).thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS,
                () -> spaceCommandService.deactivate(1L, "점검")
        );
    }

    @Test
    void allowsInactiveCapacityReduction() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space updated = spaceCommandService.update(
                1L,
                new UpdateSpaceCommand(
                        "회의실 A",
                        SpaceType.MEETING,
                        4
                )
        );

        assertThat(updated.getCapacity()).isEqualTo(4);
    }

    @Test
    void rejectsActiveCapacityReduction() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        null
                )));

        assertBusinessError(
                SpaceErrorCode.ACTIVE_CAPACITY_REDUCTION_NOT_ALLOWED,
                () -> spaceCommandService.update(
                        1L,
                        new UpdateSpaceCommand(
                                "회의실 A",
                                SpaceType.MEETING,
                                4
                        )
                )
        );
    }

    @Test
    void rejectsTypeChangeWhenActiveOccupancyExists() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceOccupancyQueryPort.existsActiveOccupancy(
                any(Long.class),
                any(ZonedDateTime.class)
        )).thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS,
                () -> spaceCommandService.update(
                        1L,
                        new UpdateSpaceCommand(
                                "회의실 A",
                                SpaceType.STUDY,
                                8
                        )
                )
        );
    }

    @Test
    void doesNotQueryOccupancyWhenTypeIsUnchanged() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        null
                )));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space updated = spaceCommandService.update(
                1L,
                new UpdateSpaceCommand(
                        "회의실 A",
                        SpaceType.MEETING,
                        9
                )
        );

        assertThat(updated.getCapacity()).isEqualTo(9);
        verify(spaceOccupancyQueryPort, never())
                .existsActiveOccupancy(
                        any(Long.class),
                        any(ZonedDateTime.class)
                );
    }

    @Test
    void activeTypeChangeFailsBeforeOccupancyQueryOrSave() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        null
                )));

        assertBusinessError(
                SpaceErrorCode.ACTIVE_TYPE_CHANGE_NOT_ALLOWED,
                () -> spaceCommandService.update(
                        1L,
                        new UpdateSpaceCommand(
                                "회의실 A",
                                SpaceType.STUDY,
                                8
                        )
                )
        );
        verify(spaceOccupancyQueryPort, never())
                .existsActiveOccupancy(
                        any(Long.class),
                        any(ZonedDateTime.class)
                );
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    void rejectsDeleteWhenActiveOccupancyExists() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceOccupancyQueryPort.existsActiveOccupancy(
                any(Long.class),
                any(ZonedDateTime.class)
        )).thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS,
                () -> spaceCommandService.delete(1L)
        );
    }

    @Test
    void rejectsChangingDeletedSpace() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.INACTIVE,
                        null,
                        ZonedDateTime.ofInstant(NOW, SEOUL)
                )));

        assertBusinessError(
                SpaceErrorCode.DELETED_SPACE,
                () -> spaceCommandService.update(
                        1L,
                        new UpdateSpaceCommand(
                                "회의실 B",
                                SpaceType.MEETING,
                                8
                        )
                )
        );
    }

    @Test
    void rejectsDeletingUnmanagedSpace() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(unmanagedInactiveSpace()));

        assertBusinessError(
                SpaceErrorCode.UNMANAGED_SPACE_DELETE_NOT_ALLOWED,
                () -> spaceCommandService.delete(1L)
        );
    }

    @Test
    void rejectsChangingAssignedLabTypeWithSpecificError() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(assignedLab()));

        assertBusinessError(
                SpaceErrorCode.ASSIGNED_LAB_TYPE_CHANGE_NOT_ALLOWED,
                () -> spaceCommandService.update(
                        1L,
                        new UpdateSpaceCommand(
                                "실습실 A",
                                SpaceType.STUDY,
                                20
                        )
                )
        );
    }

    @Test
    void rejectsDeletingAssignedLabWithSpecificError() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(assignedLab()));

        assertBusinessError(
                SpaceErrorCode.ASSIGNED_LAB_DELETE_NOT_ALLOWED,
                () -> spaceCommandService.delete(1L)
        );
    }

    @Test
    void rejectsNullTypeFromDirectServiceCall() {
        when(spaceRepository.findById(1L))
                .thenReturn(Optional.of(existingSpace()));

        assertBusinessError(
                SpaceErrorCode.INVALID_TYPE,
                () -> spaceCommandService.update(
                        1L,
                        new UpdateSpaceCommand(
                                "회의실 A",
                                null,
                                8
                        )
                )
        );
    }

    private Space existingSpace() {
        return space(SpaceOperationalStatus.INACTIVE, null, null);
    }

    private Space space(
            SpaceOperationalStatus operationalStatus,
            String inactiveReason,
            ZonedDateTime deletedAt
    ) {
        ZonedDateTime now = ZonedDateTime.ofInstant(NOW, SEOUL);

        return Space.restore(
                1L,
                42L,
                "회의실 A",
                SpaceType.MEETING,
                8,
                operationalStatus,
                inactiveReason,
                now,
                now,
                deletedAt
        );
    }

    private Space unmanagedInactiveSpace() {
        ZonedDateTime now = ZonedDateTime.ofInstant(NOW, SEOUL);

        return Space.restore(
                1L,
                null,
                "회의실 A",
                SpaceType.MEETING,
                8,
                SpaceOperationalStatus.INACTIVE,
                null,
                now,
                now,
                null
        );
    }

    private Space assignedLab() {
        ZonedDateTime now = ZonedDateTime.ofInstant(NOW, SEOUL);

        return Space.restore(
                1L,
                42L,
                "실습실 A",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.INACTIVE,
                "운영 준비 중",
                now,
                now,
                null
        );
    }

    private void assertBusinessError(
            SpaceErrorCode expectedErrorCode,
            ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(expectedErrorCode));
    }
}
