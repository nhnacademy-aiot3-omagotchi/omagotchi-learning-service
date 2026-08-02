package site.omagotchi.learningservice.space.application.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.space.application.port.out.SpaceQueryPort;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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

    private SpaceQueryService spaceQueryService;

    @BeforeEach
    void setUp() {
        spaceQueryService = new SpaceQueryService(
                spaceQueryPort,
                Clock.fixed(NOW, SEOUL)
        );
    }

    @Test
    void returnsSpaceListFromPortAtCurrentClockTime() {
        List<SpaceListItem> expected = List.of(new SpaceListItem(
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
        when(spaceQueryPort.findAllSpacesWithStatus(SEOUL_NOW))
                .thenReturn(expected);

        List<SpaceListItem> actual = spaceQueryService.getSpaceList();

        assertThat(actual).isSameAs(expected);
        verify(spaceQueryPort).findAllSpacesWithStatus(SEOUL_NOW);
        verifyNoMoreInteractions(spaceQueryPort);
    }

    @Test
    void returnsEmptyListWhenPortHasNoSpaces() {
        when(spaceQueryPort.findAllSpacesWithStatus(SEOUL_NOW))
                .thenReturn(List.of());

        assertThat(spaceQueryService.getSpaceList()).isEmpty();

        verify(spaceQueryPort).findAllSpacesWithStatus(SEOUL_NOW);
        verifyNoMoreInteractions(spaceQueryPort);
    }
}
