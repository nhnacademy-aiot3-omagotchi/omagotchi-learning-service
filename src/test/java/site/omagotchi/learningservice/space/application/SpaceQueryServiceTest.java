package site.omagotchi.learningservice.space.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.space.application.port.SpaceQueryPort;
import site.omagotchi.learningservice.space.application.port.SpaceCohortAccessPort;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");
    private static final ZonedDateTime SEOUL_NOW =
            ZonedDateTime.ofInstant(NOW, SEOUL);

    @Mock
    private SpaceQueryPort spaceQueryPort;

    @Mock
    private SpaceCohortAccessPort cohortAccessPort;

    private SpaceQueryService spaceQueryService;

    @BeforeEach
    void setUp() {
        spaceQueryService = new SpaceQueryService(
                spaceQueryPort,
                cohortAccessPort,
                Clock.fixed(NOW, SEOUL)
        );
    }

    @Test
    void returnsSpaceListFromPortAtCurrentClockTime() {
        List<SpaceListResult> expected = List.of(new SpaceListResult(
                1L,
                "회의실 A",
                SpaceType.MEETING,
                8,
                SpaceOperationalStatus.ACTIVE,
                null,
                null,
                SpaceUsageStatus.AVAILABLE,
                null,
                null
        ));
        when(spaceQueryPort.findAllSpacesWithStatus(Set.of(), SEOUL_NOW))
                .thenReturn(expected);

        List<SpaceListResult> actual = spaceQueryService.getSpaceList(null);

        assertThat(actual).isSameAs(expected);
        verify(spaceQueryPort).findAllSpacesWithStatus(Set.of(), SEOUL_NOW);
        verifyNoInteractions(cohortAccessPort);
        verifyNoMoreInteractions(spaceQueryPort);
    }

    @Test
    void returnsEmptyListWhenPortHasNoSpaces() {
        when(spaceQueryPort.findAllSpacesWithStatus(Set.of(), SEOUL_NOW))
                .thenReturn(List.of());

        assertThat(spaceQueryService.getSpaceList(null)).isEmpty();

        verify(spaceQueryPort).findAllSpacesWithStatus(Set.of(), SEOUL_NOW);
        verifyNoInteractions(cohortAccessPort);
        verifyNoMoreInteractions(spaceQueryPort);
    }

    @Test
    void passesRequestersActiveCohortsToQueryPort() {
        UUID requesterUserId = UUID.randomUUID();
        when(cohortAccessPort.findActiveCohortIds(requesterUserId))
                .thenReturn(List.of(11L, 12L));
        when(spaceQueryPort.findAllSpacesWithStatus(
                Set.of(11L, 12L),
                SEOUL_NOW
        )).thenReturn(List.of());

        assertThat(spaceQueryService.getSpaceList(requesterUserId)).isEmpty();

        verify(cohortAccessPort).findActiveCohortIds(requesterUserId);
        verify(spaceQueryPort).findAllSpacesWithStatus(
                Set.of(11L, 12L),
                SEOUL_NOW
        );
    }
}
