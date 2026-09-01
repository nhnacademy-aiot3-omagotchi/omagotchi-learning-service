package site.omagotchi.learningservice.space.application;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.PresenceSpaceQueryService;
import site.omagotchi.learningservice.attendance.application.result.SpacePresenceSummary;
import site.omagotchi.learningservice.cohort.application.CohortLockService;
import site.omagotchi.learningservice.cohort.application.result.CohortLockView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.space.application.port.SpaceReferenceQueryPort;
import site.omagotchi.learningservice.space.application.port.SpaceRepository;
import site.omagotchi.learningservice.space.application.port.SpaceLabReductionQueryPort;
import site.omagotchi.learningservice.space.application.result.SpaceLabReductionView;
import site.omagotchi.learningservice.occupancy.application.OccupancyQueryService;
import site.omagotchi.learningservice.occupancy.application.VacancyAlertService;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private SpaceLabReductionQueryPort spaceLabReductionQueryPort;

    @Mock
    private OccupancyQueryService occupancyQueryService;

    @Mock
    private VacancyAlertService vacancyAlertService;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private CohortLockService cohortLockService;

    @Mock
    private PresenceSpaceQueryService presenceSpaceQueryService;

    @Mock
    private SpaceReferenceQueryPort spaceReferenceQueryPort;

    private TestSpaceCommandService spaceCommandService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, SEOUL);
        SpaceCommandService delegate = new SpaceCommandService(
                spaceRepository,
                spaceLabReductionQueryPort,
                occupancyQueryService,
                vacancyAlertService,
                cohortAccessService,
                spaceReferenceQueryPort,
                cohortLockService,
                presenceSpaceQueryService,
                clock
        );
        spaceCommandService = new TestSpaceCommandService(delegate);
        lenient().when(cohortAccessService.exists(anyLong()))
                .thenReturn(true);
        lenient().when(cohortAccessService.isManager(
                        anyLong(),
                        any(UUID.class)
                ))
                .thenReturn(true);
        lenient().when(spaceLabReductionQueryPort.find(anyLong()))
                .thenAnswer(invocation -> Optional.of(new SpaceLabReductionView(
                        invocation.getArgument(0),
                        42L,
                        false
                )));
        lenient().when(presenceSpaceQueryService.summarize(anyLong()))
                .thenReturn(SpacePresenceSummary.empty());
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
        verify(cohortAccessService).isManager(42L, ACTOR_USER_ID);
    }

    @Test
    void usesActorsActiveManagedCohortWhenCreateCohortIdIsOmitted() {
        when(cohortAccessService.findActiveManagedCohortIds(ACTOR_USER_ID))
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
        when(cohortAccessService.findActiveManagedCohortIds(ACTOR_USER_ID))
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
        when(cohortAccessService.findActiveManagedCohortIds(ACTOR_USER_ID))
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
        when(cohortAccessService.isManager(84L, ACTOR_USER_ID))
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
        verify(cohortAccessService).isManager(42L, ACTOR_USER_ID);
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
        verify(cohortAccessService).isManager(42L, ACTOR_USER_ID);
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
        verify(cohortAccessService).isManager(42L, ACTOR_USER_ID);
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
                SpaceErrorCode.NOT_FOUND,
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
                SpaceErrorCode.NOT_FOUND,
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
                SpaceErrorCode.NOT_FOUND,
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
        verify(cohortAccessService, times(2)).isManager(42L, ACTOR_USER_ID);
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
        when(occupancyQueryService.existsActive(
                eq(1L),
                eq(ZonedDateTime.ofInstant(NOW, SEOUL).toOffsetDateTime())
        )).thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS,
                () -> spaceCommandService.deactivate(1L, "점검")
        );
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    @DisplayName("현재 체류자가 있으면 공간 비활성화를 거절한다")
    void rejectsDeactivationWhenCurrentPresenceExists() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(space(
                        SpaceOperationalStatus.ACTIVE,
                        null,
                        null
                )));
        when(presenceSpaceQueryService.summarize(1L))
                .thenReturn(new SpacePresenceSummary(1L, 0L));

        assertBusinessError(
                SpaceErrorCode.SPACE_HAS_CURRENT_PRESENCE,
                () -> spaceCommandService.deactivate(1L, "점검")
        );
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    @DisplayName("활성 기수의 마지막 활성 실습실은 비활성화할 수 없다")
    void rejectsLastActiveLabDeactivationForActiveCohort() {
        Space activeLab = activeLab();
        when(spaceLabReductionQueryPort.find(1L))
                .thenReturn(Optional.of(new SpaceLabReductionView(1L, 42L, true)));
        when(cohortLockService.lock(42L))
                .thenReturn(new CohortLockView(42L, true));
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(activeLab));
        when(spaceRepository.countActiveLabsByCohortId(42L)).thenReturn(1L);

        assertBusinessError(
                SpaceErrorCode.LAST_ACTIVE_LAB_REQUIRED,
                () -> spaceCommandService.deactivate(1L, "점검")
        );
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    @DisplayName("회의 종료 후 복귀 예약이 있으면 실습실 비활성화를 거절한다")
    void rejectsLabDeactivationWhenReturnReservationExists() {
        Space activeLab = activeLab();
        when(spaceLabReductionQueryPort.find(1L))
                .thenReturn(Optional.of(new SpaceLabReductionView(1L, 42L, true)));
        when(cohortLockService.lock(42L))
                .thenReturn(new CohortLockView(42L, true));
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(activeLab));
        when(spaceRepository.countActiveLabsByCohortId(42L)).thenReturn(2L);
        when(presenceSpaceQueryService.summarize(1L))
                .thenReturn(new SpacePresenceSummary(0L, 1L));

        assertBusinessError(
                SpaceErrorCode.SPACE_HAS_RETURN_RESERVATION,
                () -> spaceCommandService.deactivate(1L, "점검")
        );
    }

    @Test
    @DisplayName("권한 없는 실습실 비활성화 요청은 기수 잠금 전에 거절한다")
    void rejectsUnauthorizedLabDeactivationBeforeCohortLock() {
        when(spaceLabReductionQueryPort.find(1L))
                .thenReturn(Optional.of(new SpaceLabReductionView(1L, 42L, true)));
        when(cohortAccessService.isManager(42L, ACTOR_USER_ID)).thenReturn(false);

        assertBusinessError(
                SpaceErrorCode.ACCESS_DENIED,
                () -> spaceCommandService.deactivate(1L, "점검")
        );

        verify(cohortLockService, never()).lock(anyLong());
        verify(spaceRepository, never()).findByIdForUpdate(anyLong());
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
        when(occupancyQueryService.existsActive(
                eq(1L),
                eq(ZonedDateTime.ofInstant(NOW, SEOUL).toOffsetDateTime())
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
        verify(occupancyQueryService, never())
                .existsActive(
                        any(Long.class),
                        any(OffsetDateTime.class)
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
        verify(occupancyQueryService, never())
                .existsActive(
                        any(Long.class),
                        any(OffsetDateTime.class)
                );
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    void rejectsDeletingSpaceWhenSensorIsPlaced() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceReferenceQueryPort.countSensors(1L)).thenReturn(2L);

        // 센서가 남은 채 삭제하면 그 센서는 어느 기수에서도 보이지 않게 된다
        assertBusinessError(
                SpaceErrorCode.SPACE_HAS_SENSOR_DELETE_NOT_ALLOWED,
                () -> spaceCommandService.delete(1L)
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

        verify(occupancyQueryService).existsActive(
                eq(1L),
                eq(ZonedDateTime.ofInstant(NOW, SEOUL).toOffsetDateTime())
        );
    }

    @Test
    void rejectsDeletingSpaceWhenActiveOccupancyExists() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(occupancyQueryService.existsActive(
                eq(1L),
                eq(ZonedDateTime.ofInstant(NOW, SEOUL).toOffsetDateTime())
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
                SpaceErrorCode.NOT_FOUND,
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
                SpaceErrorCode.SPACE_ALREADY_ASSIGNED,
                () -> spaceCommandService.assignCohort(1L, 42L)
        );
        assertBusinessError(
                SpaceErrorCode.SPACE_ALREADY_ASSIGNED,
                () -> spaceCommandService.assignCohort(1L, 84L)
        );
    }

    /**
     * <p>이 인수 경로가 없으면 <b>기수 종료로 주체가 풀린 회의실·독서실을 영구히 삭제할 수
     * 없다</b> — 관리 주체 없는 공간은 삭제가 막히므로(RM-25) 누군가 책임을 선언해야 한다.</p>
     */
    @Test
    @DisplayName("회의실도 기수가 인수할 수 있다")
    void allowsCohortAssignmentToNonLab() {
        // 주체 없는 회의실이어야 한다 — existingSpace()는 이미 42기에 배정돼 있다.
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(unmanagedInactiveSpace()));
        when(cohortAccessService.exists(42L)).thenReturn(true);
        when(cohortAccessService.isManager(42L, ACTOR_USER_ID)).thenReturn(true);
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Space assigned = spaceCommandService.assignCohort(1L, 42L);

        assertThat(assigned.getCohortId()).isEqualTo(42L);
    }

    @Test
    void rejectsAssigningDeletedLab() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(
                        null,
                        ZonedDateTime.ofInstant(NOW, SEOUL)
                )));

        assertBusinessError(
                SpaceErrorCode.NOT_FOUND,
                () -> spaceCommandService.assignCohort(1L, 42L)
        );
    }

    /**
     * 이미 배정된 실습실은 대상 기수 매니저인 요청자라면 누구든 409다 (명세 07 §5).
     *
     * <p>소유 기수의 매니저인지는 묻지 않는다. 그 권한을 먼저 보면 같은 상황이 요청자에 따라
     * 403과 409로 갈려, 클라이언트가 "권한만 있으면 배정된다"고 오해한다.</p>
     */
    @Test
    @DisplayName("이미 배정된 실습실은 소유 기수 매니저가 아니어도 409다")
    void rejectsAssignmentToAlreadyAssignedLabRegardlessOfOwningCohort() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(42L, null)));
        when(cohortAccessService.exists(84L)).thenReturn(true);
        when(cohortAccessService.isManager(84L, ACTOR_USER_ID))
                .thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.SPACE_ALREADY_ASSIGNED,
                () -> spaceCommandService.assignCohort(1L, 84L)
        );

        // 소유 기수(42) 권한은 조회하지 않는다.
        verify(cohortAccessService, never()).isManager(42L, ACTOR_USER_ID);
    }

    @Test
    void rejectsAssignmentWhenActorDoesNotManageRequestedCohort() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(42L, null)));
        when(cohortAccessService.isManager(84L, ACTOR_USER_ID))
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

    @Test
    @DisplayName("활성 점유가 있는 공간은 기수 배정을 해제할 수 없다")
    void rejectsUnassigningSpaceWithActiveOccupancy() {
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(lab(42L, null)));
        when(occupancyQueryService.existsActive(
                eq(1L),
                eq(ZonedDateTime.ofInstant(NOW, SEOUL).toOffsetDateTime())
        )).thenReturn(true);

        assertBusinessError(
                SpaceErrorCode.ACTIVE_OCCUPANCY_EXISTS,
                () -> spaceCommandService.unassignCohort(1L)
        );
        verify(spaceRepository, never()).save(any(Space.class));
    }

    @Test
    @DisplayName("활성 기수의 마지막 활성 실습실은 기수 배정을 해제할 수 없다")
    void rejectsUnassigningLastActiveLabForActiveCohort() {
        when(spaceLabReductionQueryPort.find(1L))
                .thenReturn(Optional.of(new SpaceLabReductionView(1L, 42L, true)));
        when(cohortLockService.lock(42L))
                .thenReturn(new CohortLockView(42L, true));
        when(spaceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(activeLab()));
        when(spaceRepository.countActiveLabsByCohortId(42L)).thenReturn(1L);

        assertBusinessError(
                SpaceErrorCode.LAST_ACTIVE_LAB_REQUIRED,
                () -> spaceCommandService.unassignCohort(1L)
        );
        verify(spaceRepository, never()).save(any(Space.class));
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

    private Space activeLab() {
        ZonedDateTime now = ZonedDateTime.ofInstant(NOW, SEOUL);
        return Space.restore(
                1L,
                42L,
                "실습실 A",
                SpaceType.LAB,
                20,
                SpaceOperationalStatus.ACTIVE,
                null,
                now.minusDays(1),
                now.minusHours(1),
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
