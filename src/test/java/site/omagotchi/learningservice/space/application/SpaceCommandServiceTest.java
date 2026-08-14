package site.omagotchi.learningservice.space.application;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.SpaceCohortAccessPort;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.application.port.SpaceOccupancyQueryPort;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceCommandServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");
    private static final UUID ACTOR_USER_ID = UUID.fromString(
            "019d2a48-80c0-4d6a-9a15-0b16d2dd74f1"
    );

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceOccupancyQueryPort spaceOccupancyQueryPort;

    @Mock
    private SpaceCohortAccessPort cohortAccessPort;

    private TestSpaceCommandService spaceCommandService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, SEOUL);
        SpaceCommandService delegate = new SpaceCommandService(
                spaceRepository,
                spaceOccupancyQueryPort,
                cohortAccessPort,
                clock
        );
        spaceCommandService = new TestSpaceCommandService(delegate);
        lenient().when(cohortAccessPort.exists(anyLong()))
                .thenReturn(true);
        lenient().when(cohortAccessPort.isActiveManager(
                        anyLong(),
                        any(UUID.class)
                ))
                .thenReturn(true);
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
                                8,
                                42L
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
                        8,
                        42L
                )
        );

        assertThat(created.getName()).isEqualTo("회의실 A");
    }

    @Test
    void rejectsDuplicateNameOnUpdate() {
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(999L))
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
        when(spaceRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        assertBusinessError(
                SpaceErrorCode.NOT_FOUND,
                () -> spaceCommandService.delete(999L)
        );
    }

    @Test
    void rejectsActivatingMissingSpace() {
        when(spaceRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        assertBusinessError(
                SpaceErrorCode.NOT_FOUND,
                () -> spaceCommandService.activate(999L)
        );
    }

    @Test
    void rejectsDeactivatingMissingSpace() {
        when(spaceRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        assertBusinessError(
                SpaceErrorCode.NOT_FOUND,
                () -> spaceCommandService.deactivate(999L, "점검")
        );
    }

    @Test
    void createsManagedInactiveSpaceAfterManagerAuthorization() {
        when(spaceRepository.existsActiveByName("회의실 A"))
                .thenReturn(false);
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space created = spaceCommandService.create(
                new CreateSpaceCommand(
                        " 회의실 A ",
                        SpaceType.MEETING,
                        8,
                        42L
                )
        );

        assertThat(created.getName()).isEqualTo("회의실 A");
        assertThat(created.getCohortId()).isEqualTo(42L);
        assertThat(created.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
        verify(cohortAccessPort).isActiveManager(42L, ACTOR_USER_ID);
    }

    @Test
    void usesActorsActiveManagedCohortWhenCreateCohortIdIsOmitted() {
        when(cohortAccessPort.findActiveManagedCohortIds(ACTOR_USER_ID))
                .thenReturn(List.of(42L));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space created = spaceCommandService.create(new CreateSpaceCommand(
                "회의실 A",
                SpaceType.MEETING,
                8,
                null
        ));

        assertThat(created.getCohortId()).isEqualTo(42L);
        assertThat(created.getOperationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
    }

    @Test
    void rejectsCreateWithoutActiveManagedCohort() {
        when(cohortAccessPort.findActiveManagedCohortIds(ACTOR_USER_ID))
                .thenReturn(List.of());

        assertBusinessError(
                SpaceErrorCode.ACTIVE_COHORT_NOT_FOUND,
                () -> spaceCommandService.create(new CreateSpaceCommand(
                        "회의실 A",
                        SpaceType.MEETING,
                        8,
                        null
                ))
        );
    }

    @Test
    void requiresCohortIdWhenActorManagesMultipleActiveCohorts() {
        when(cohortAccessPort.findActiveManagedCohortIds(ACTOR_USER_ID))
                .thenReturn(List.of(42L, 84L));

        assertBusinessError(
                SpaceErrorCode.COHORT_ID_REQUIRED,
                () -> spaceCommandService.create(new CreateSpaceCommand(
                        "회의실 A",
                        SpaceType.MEETING,
                        8,
                        null
                ))
        );
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    void rejectsCreateForAnotherCohort() {
        when(cohortAccessPort.isActiveManager(84L, ACTOR_USER_ID))
                .thenReturn(false);

        assertBusinessError(
                SpaceErrorCode.ACCESS_DENIED,
                () -> spaceCommandService.create(new CreateSpaceCommand(
                        "회의실 A",
                        SpaceType.MEETING,
                        8,
                        84L
                ))
        );
    }

    @Test
    void updatesSpaceWithoutChangingExistingBehavior() {
        when(spaceRepository.findByIdForUpdate(1L))
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
        verify(cohortAccessPort).isActiveManager(42L, ACTOR_USER_ID);
        verify(spaceRepository).findByIdForUpdate(1L);
    }

    @Test
    void deletesSpaceWithoutChangingExistingBehavior() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);

        spaceCommandService.delete(1L);

        verify(spaceRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getCohortId()).isEqualTo(42L);
        verify(cohortAccessPort).isActiveManager(42L, ACTOR_USER_ID);
        verify(spaceRepository).findByIdForUpdate(1L);
    }

    @Test
    void activatesInactiveSpaceAndClearsInactiveReason() {
        when(spaceRepository.findByIdForUpdate(1L))
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
        verify(cohortAccessPort).isActiveManager(42L, ACTOR_USER_ID);
        verify(spaceRepository).findByIdForUpdate(1L);
    }

    @Test
    void rejectsActivatingAlreadyActiveSpace() {
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
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
        verify(cohortAccessPort).isActiveManager(42L, ACTOR_USER_ID);
        verify(spaceRepository).findByIdForUpdate(1L);
    }

    @Test
    void rejectsDeactivatingAlreadyInactiveSpace() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingSpace()));

        assertBusinessError(
                SpaceErrorCode.ALREADY_INACTIVE,
                () -> spaceCommandService.deactivate(1L, "점검")
        );
    }

    @Test
    void rejectsBlankInactiveReason() {
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
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
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    void allowsInactiveCapacityReduction() {
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
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
    void deletesInactiveSpaceAfterCheckingActiveOccupancy() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        spaceCommandService.delete(1L);

        verify(spaceOccupancyQueryPort).existsActiveOccupancy(
                any(Long.class),
                any(ZonedDateTime.class)
        );
    }

    @Test
    void rejectsDeletingSpaceWhenActiveOccupancyExists() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceOccupancyQueryPort.existsActiveOccupancy(
                any(Long.class),
                any(ZonedDateTime.class)
        )).thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS,
                () -> spaceCommandService.delete(1L)
        );

        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    void rejectsChangingDeletedSpace() {
        when(spaceRepository.findByIdForUpdate(1L))
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
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(unmanagedInactiveSpace()));

        assertBusinessError(
                SpaceErrorCode.UNMANAGED_SPACE_DELETE_NOT_ALLOWED,
                () -> spaceCommandService.delete(1L)
        );
    }

    @Test
    @DisplayName("배정된 실습실이어도 비활성이면 유형을 변경할 수 있다 (RM-22 미수용)")
    void allowsChangingAssignedLabTypeWhenInactive() {
        Space assigned = assignedLab();
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(assigned));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space updated = spaceCommandService.update(
                1L,
                new UpdateSpaceCommand("실습실 A", SpaceType.STUDY, assigned.getCapacity())
        );

        assertThat(updated.getSpaceType()).isEqualTo(SpaceType.STUDY);
    }

    @Test
    void deletesAssignedInactiveLab() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(assignedLab()));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        spaceCommandService.delete(1L);

        verify(spaceRepository).save(any(Space.class));
    }

    @Test
    void rejectsNullTypeFromDirectServiceCall() {
        when(spaceRepository.findByIdForUpdate(1L))
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

    @Test
    void rejectsInvalidNameFromDirectServiceCall() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingSpace()));

        assertBusinessError(
                SpaceErrorCode.INVALID_NAME,
                () -> spaceCommandService.update(
                        1L,
                        new UpdateSpaceCommand(
                                "   ",
                                SpaceType.MEETING,
                                8
                        )
                )
        );
    }

    @Test
    void rejectsInvalidCapacityFromDirectServiceCall() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingSpace()));

        assertBusinessError(
                SpaceErrorCode.INVALID_CAPACITY,
                () -> spaceCommandService.update(
                        1L,
                        new UpdateSpaceCommand(
                                "회의실 A",
                                SpaceType.MEETING,
                                0
                        )
                )
        );
    }

    @Test
    void assignsCohortToUnassignedLabAndUpdatesTimestamp() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(null, null)));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space assigned = spaceCommandService.assignCohort(1L, 42L);

        assertThat(assigned.getCohortId()).isEqualTo(42L);
        assertThat(assigned.getUpdatedAt())
                .isEqualTo(ZonedDateTime.ofInstant(NOW, SEOUL));
    }

    @Test
    void rejectsAssigningAlreadyAssignedLabToSameOrOtherCohort() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(42L, null)));

        assertBusinessError(
                SpaceErrorCode.LAB_ALREADY_ASSIGNED,
                () -> spaceCommandService.assignCohort(1L, 42L)
        );
        assertBusinessError(
                SpaceErrorCode.LAB_ALREADY_ASSIGNED,
                () -> spaceCommandService.assignCohort(1L, 84L)
        );
    }

    @Test
    void rejectsCohortAssignmentToNonLab() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingSpace()));

        assertBusinessError(
                SpaceErrorCode.LAB_ONLY_COHORT_ASSIGNMENT,
                () -> spaceCommandService.assignCohort(1L, 42L)
        );
    }

    @Test
    void rejectsAssigningDeletedLab() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(
                        null,
                        ZonedDateTime.ofInstant(NOW, SEOUL)
                )));

        assertBusinessError(
                SpaceErrorCode.DELETED_SPACE,
                () -> spaceCommandService.assignCohort(1L, 42L)
        );
    }

    @Test
    void rejectsAssignmentWhenActorDoesNotManageOwningCohort() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(42L, null)));
        when(cohortAccessPort.isActiveManager(42L, ACTOR_USER_ID))
                .thenReturn(false);

        assertBusinessError(
                SpaceErrorCode.ACCESS_DENIED,
                () -> spaceCommandService.assignCohort(1L, 84L)
        );
    }

    @Test
    void rejectsAssignmentWhenActorDoesNotManageRequestedCohort() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(42L, null)));
        when(cohortAccessPort.isActiveManager(84L, ACTOR_USER_ID))
                .thenReturn(false);

        assertBusinessError(
                SpaceErrorCode.ACCESS_DENIED,
                () -> spaceCommandService.assignCohort(1L, 84L)
        );
    }

    @Test
    void unassignsLabAndUpdatesTimestamp() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(42L, null)));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space unassigned = spaceCommandService.unassignCohort(1L);

        assertThat(unassigned.getCohortId()).isNull();
        assertThat(unassigned.getUpdatedAt())
                .isEqualTo(ZonedDateTime.ofInstant(NOW, SEOUL));
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

    private Space lab(Long cohortId, ZonedDateTime deletedAt) {
        ZonedDateTime now = ZonedDateTime.ofInstant(NOW, SEOUL);

        return Space.restore(
                1L,
                cohortId,
                "실습실 A",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.INACTIVE,
                null,
                now.minusDays(1),
                now.minusHours(1),
                deletedAt
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

    private static class TestSpaceCommandService {

        private final SpaceCommandService delegate;

        private TestSpaceCommandService(SpaceCommandService delegate) {
            this.delegate = delegate;
        }

        private Space create(CreateSpaceCommand command) {
            return delegate.create(command, ACTOR_USER_ID);
        }

        private Space update(
                Long spaceId,
                UpdateSpaceCommand command
        ) {
            return delegate.update(
                    spaceId,
                    command,
                    ACTOR_USER_ID);
        }

        private Space activate(Long spaceId) {
            return delegate.activate(
                    spaceId,
                    ACTOR_USER_ID);
        }

        private Space deactivate(
                Long spaceId,
                String reason
        ) {
            return delegate.deactivate(
                    spaceId,
                    reason,
                    ACTOR_USER_ID);
        }

        private void delete(Long spaceId) {
            delegate.delete(spaceId, ACTOR_USER_ID);
        }

        private Space assignCohort(Long spaceId, Long cohortId) {
            return delegate.assignCohort(
                    spaceId,
                    cohortId,
                    ACTOR_USER_ID);
        }

        private Space unassignCohort(Long spaceId) {
            return delegate.unassignCohort(
                    spaceId,
                    ACTOR_USER_ID);
        }
    }
}
