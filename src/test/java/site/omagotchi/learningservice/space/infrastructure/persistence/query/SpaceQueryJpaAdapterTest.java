package site.omagotchi.learningservice.space.infrastructure.persistence.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.space.application.query.SpaceListItem;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.RoomOccupancyJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceQueryJpaAdapterTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZonedDateTime NOW = ZonedDateTime.of(
            2026, 7, 27, 14, 0, 0, 0, SEOUL
    );

    @Mock
    private SpringDataSpaceRepository spaceRepository;

    @Mock
    private SpringDataRoomOccupancyRepository roomOccupancyRepository;

    private SpaceQueryJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpaceQueryJpaAdapter(
                spaceRepository,
                roomOccupancyRepository
        );
    }

    @Test
    void returnsAvailableForActiveMeetingWithoutOccupancy() {
        SpaceJpaEntity space = space(
                1L,
                SpaceType.MEETING,
                SpaceOperationalStatus.ACTIVE,
                null,
                11L
        );
        stubSpaces(List.of(space), List.of());

        SpaceListItem item = findOnlyItem();

        assertThat(item.operationalStatus())
                .isEqualTo(SpaceOperationalStatus.ACTIVE);
        assertThat(item.status())
                .isEqualTo(SpaceUsageStatus.AVAILABLE);
        assertThat(item.occupancyExpiresAt()).isNull();
        assertThat(item.remainingTimeSeconds()).isNull();
    }

    @Test
    void returnsOccupiedForActiveMeetingWithValidOccupancy() {
        SpaceJpaEntity space = space(
                1L,
                SpaceType.MEETING,
                SpaceOperationalStatus.ACTIVE,
                null,
                11L
        );
        RoomOccupancyJpaEntity occupancy = occupancy(
                1L,
                NOW.plusMinutes(30).toOffsetDateTime()
        );
        stubSpaces(List.of(space), List.of(occupancy));

        SpaceListItem item = findOnlyItem();

        assertThat(item.operationalStatus())
                .isEqualTo(SpaceOperationalStatus.ACTIVE);
        assertThat(item.status())
                .isEqualTo(SpaceUsageStatus.OCCUPIED);
        assertThat(item.occupancyExpiresAt())
                .isEqualTo(NOW.plusMinutes(30));
        assertThat(item.remainingTimeSeconds()).isEqualTo(1800L);
        assertThat(item.remainingTimeSeconds()).isNotNegative();
    }

    @Test
    void returnsUnavailableForInactiveMeetingAndHidesOccupancy() {
        SpaceJpaEntity space = space(
                1L,
                SpaceType.MEETING,
                SpaceOperationalStatus.INACTIVE,
                "시설 점검",
                11L
        );
        RoomOccupancyJpaEntity occupancy = occupancy(
                1L,
                NOW.plusMinutes(30).toOffsetDateTime()
        );
        stubSpaces(List.of(space), List.of(occupancy));

        SpaceListItem item = findOnlyItem();

        assertThat(item.operationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
        assertThat(item.status())
                .isEqualTo(SpaceUsageStatus.UNAVAILABLE);
        assertThat(item.inactiveReason()).isEqualTo("시설 점검");
        assertThat(item.cohortId()).isEqualTo(11L);
        assertThat(item.occupancyExpiresAt()).isNull();
        assertThat(item.remainingTimeSeconds()).isNull();
    }

    @Test
    void returnsNotApplicableForActiveLabAndIgnoresOccupancy() {
        assertNotApplicable(
                SpaceType.LAB,
                SpaceOperationalStatus.ACTIVE,
                true
        );
    }

    @Test
    void returnsNotApplicableForInactiveLab() {
        assertNotApplicable(
                SpaceType.LAB,
                SpaceOperationalStatus.INACTIVE,
                false
        );
    }

    @Test
    void returnsNotApplicableForActiveStudy() {
        assertNotApplicable(
                SpaceType.STUDY,
                SpaceOperationalStatus.ACTIVE,
                false
        );
    }

    @Test
    void queriesOnlyNonDeletedSpaces() {
        when(spaceRepository.findAllByDeletedAtIsNullOrderByIdAsc())
                .thenReturn(List.of());

        assertThat(adapter.findAllSpacesWithStatus(NOW)).isEmpty();

        verify(spaceRepository)
                .findAllByDeletedAtIsNullOrderByIdAsc();
        verifyNoMoreInteractions(roomOccupancyRepository);
    }

    private void assertNotApplicable(
            SpaceType spaceType,
            SpaceOperationalStatus operationalStatus,
            boolean includeIncorrectOccupancy
    ) {
        SpaceJpaEntity space = space(
                1L,
                spaceType,
                operationalStatus,
                operationalStatus == SpaceOperationalStatus.INACTIVE
                        ? "운영 중단"
                        : null,
                11L
        );
        List<RoomOccupancyJpaEntity> occupancies =
                includeIncorrectOccupancy
                        ? List.of(occupancy(
                                1L,
                                NOW.plusMinutes(30).toOffsetDateTime()
                        ))
                        : List.of();
        stubSpaces(List.of(space), occupancies);

        SpaceListItem item = findOnlyItem();

        assertThat(item.operationalStatus())
                .isEqualTo(operationalStatus);
        assertThat(item.status())
                .isEqualTo(SpaceUsageStatus.NOT_APPLICABLE);
        assertThat(item.occupancyExpiresAt()).isNull();
        assertThat(item.remainingTimeSeconds()).isNull();
    }

    private void stubSpaces(
            List<SpaceJpaEntity> spaces,
            List<RoomOccupancyJpaEntity> occupancies
    ) {
        when(spaceRepository.findAllByDeletedAtIsNullOrderByIdAsc())
                .thenReturn(spaces);
        when(roomOccupancyRepository.findAllActiveBySpaceIds(
                spaces.stream().map(SpaceJpaEntity::getId).toList(),
                NOW.toOffsetDateTime()
        )).thenReturn(occupancies);
    }

    private SpaceListItem findOnlyItem() {
        List<SpaceListItem> items =
                adapter.findAllSpacesWithStatus(NOW);
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    private SpaceJpaEntity space(
            Long id,
            SpaceType spaceType,
            SpaceOperationalStatus operationalStatus,
            String inactiveReason,
            Long cohortId
    ) {
        OffsetDateTime now = NOW.toOffsetDateTime();

        return SpaceJpaEntity.from(
                id,
                cohortId,
                "공간 " + id,
                spaceType,
                8,
                operationalStatus,
                inactiveReason,
                now.minusDays(1),
                now,
                null
        );
    }

    private RoomOccupancyJpaEntity occupancy(
            Long spaceId,
            OffsetDateTime expiresAt
    ) {
        RoomOccupancyJpaEntity occupancy =
                mock(RoomOccupancyJpaEntity.class);
        when(occupancy.getSpaceId()).thenReturn(spaceId);
        lenient().when(occupancy.getExpiresAt()).thenReturn(expiresAt);
        return occupancy;
    }
}
