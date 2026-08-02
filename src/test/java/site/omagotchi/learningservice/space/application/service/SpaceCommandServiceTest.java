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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceCommandServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-24T01:00:00Z");

    @Mock
    private SpaceRepository spaceRepository;

    private SpaceCommandService spaceCommandService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, SEOUL);
        spaceCommandService = new SpaceCommandService(
                spaceRepository,
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
        when(spaceRepository.findActiveById(1L))
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
        when(spaceRepository.findActiveById(999L))
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
        when(spaceRepository.findActiveById(999L))
                .thenReturn(Optional.empty());

        assertBusinessError(
                SpaceErrorCode.NOT_FOUND,
                () -> spaceCommandService.delete(999L)
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
        when(spaceRepository.findActiveById(1L))
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
        when(spaceRepository.findActiveById(1L))
                .thenReturn(Optional.of(existingSpace()));
        when(spaceRepository.save(any(Space.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<Space> captor = ArgumentCaptor.forClass(Space.class);

        spaceCommandService.delete(1L);

        verify(spaceRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getCohortId()).isEqualTo(42L);
    }

    private Space existingSpace() {
        ZonedDateTime now = ZonedDateTime.ofInstant(NOW, SEOUL);

        return Space.restore(
                1L,
                42L,
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
