package site.omagotchi.learningservice.space.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.space.application.result.SpaceListResult;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository.ActiveOccupancyView;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataRoomOccupancyRepository.ActiveParticipantView;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceJpaQueryReaderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZonedDateTime NOW = ZonedDateTime.of(
            2026, 7, 27, 14, 0, 0, 0, SEOUL
    );

    @Mock
    private SpringDataSpaceRepository spaceRepository;

    @Mock
    private SpringDataRoomOccupancyRepository roomOccupancyRepository;

    private SpaceJpaQueryReader adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpaceJpaQueryReader(
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

        SpaceListResult item = findOnlyItem();

        assertThat(item.operationalStatus())
                .isEqualTo(SpaceOperationalStatus.ACTIVE);
        assertThat(item.status())
                .isEqualTo(SpaceUsageStatus.AVAILABLE);
        assertThat(item.occupiedBySameCohort()).isFalse();
        assertThat(item.occupancyExpiresAt()).isNull();
        assertThat(item.remainingTimeSeconds()).isNull();
        assertNoOccupancyDetails(item);
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
        ActiveOccupancyView occupancy = occupancy(
                1L,
                NOW.plusMinutes(30).toOffsetDateTime()
        );
        stubSpaces(List.of(space), List.of(occupancy));

        SpaceListResult item = findOnlyItem();

        assertThat(item.operationalStatus())
                .isEqualTo(SpaceOperationalStatus.ACTIVE);
        assertThat(item.status())
                .isEqualTo(SpaceUsageStatus.OCCUPIED);
        assertThat(item.occupancyExpiresAt())
                .isEqualTo(NOW.plusMinutes(30));
        assertThat(item.remainingTimeSeconds()).isEqualTo(1800L);
        assertThat(item.remainingTimeSeconds()).isNotNegative();
        assertThat(item.occupiedBySameCohort()).isFalse();
    }

    @Test
    void exposesOccupancyDetailsOnlyToSameCohortRequester() {
        SpaceJpaEntity space = space(
                1L,
                SpaceType.MEETING,
                SpaceOperationalStatus.ACTIVE,
                null,
                11L
        );
        UUID occupierUserId = UUID.randomUUID();
        UUID participantUserId = UUID.randomUUID();
        ActiveOccupancyView occupancy = occupancy(
                1L,
                NOW.plusMinutes(30).toOffsetDateTime()
        );
        when(occupancy.getOccupierMembershipId()).thenReturn(31L);
        when(occupancy.getOccupierUserId()).thenReturn(occupierUserId);
        ActiveParticipantView participant = mock(
                ActiveParticipantView.class
        );
        when(participant.getOccupancyId()).thenReturn(101L);
        when(participant.getUserId()).thenReturn(participantUserId);
        stubSpaces(List.of(space), List.of(occupancy));
        when(roomOccupancyRepository.findAllActiveParticipants(
                List.of(101L)
        )).thenReturn(List.of(participant));

        SpaceListResult sameCohort = adapter
                .findAllSpacesWithStatus(Set.of(21L), NOW)
                .getFirst();
        SpaceListResult otherCohort = adapter
                .findAllSpacesWithStatus(Set.of(22L), NOW)
                .getFirst();
        SpaceListResult anonymous = adapter
                .findAllSpacesWithStatus(Set.of(), NOW)
                .getFirst();

        assertThat(sameCohort.occupiedBySameCohort()).isTrue();
        assertThat(sameCohort.remainingTimeSeconds()).isEqualTo(1800L);
        assertThat(sameCohort.occupancyCohortId()).isEqualTo(21L);
        assertThat(sameCohort.occupierMembershipId()).isEqualTo(31L);
        assertThat(sameCohort.occupierUserId()).isEqualTo(occupierUserId);
        assertThat(sameCohort.participantUserIds())
                .containsExactly(participantUserId);
        assertThat(otherCohort.status())
                .isEqualTo(SpaceUsageStatus.OCCUPIED);
        assertThat(otherCohort.occupiedBySameCohort()).isFalse();
        assertThat(otherCohort.occupancyExpiresAt()).isNotNull();
        assertThat(otherCohort.remainingTimeSeconds()).isEqualTo(1800L);
        assertThat(otherCohort.occupancyCohortId()).isNull();
        assertThat(otherCohort.occupierMembershipId()).isNull();
        assertThat(otherCohort.occupierUserId()).isNull();
        assertThat(otherCohort.participantUserIds()).isNull();
        assertThat(anonymous.occupiedBySameCohort()).isFalse();
        assertThat(anonymous.remainingTimeSeconds()).isEqualTo(1800L);
        assertThat(anonymous.occupancyCohortId()).isNull();
        assertThat(anonymous.occupierMembershipId()).isNull();
        assertThat(anonymous.occupierUserId()).isNull();
        assertThat(anonymous.participantUserIds()).isNull();
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
        ActiveOccupancyView occupancy = occupancy(
                1L,
                NOW.plusMinutes(30).toOffsetDateTime()
        );
        stubSpaces(List.of(space), List.of(occupancy));

        SpaceListResult item = findOnlyItem();

        assertThat(item.operationalStatus())
                .isEqualTo(SpaceOperationalStatus.INACTIVE);
        assertThat(item.status())
                .isEqualTo(SpaceUsageStatus.UNAVAILABLE);
        assertThat(item.occupiedBySameCohort()).isFalse();
        assertThat(item.inactiveReason()).isEqualTo("시설 점검");
        assertThat(item.cohortId()).isEqualTo(11L);
        assertThat(item.occupancyExpiresAt()).isNull();
        assertThat(item.remainingTimeSeconds()).isNull();
        assertNoOccupancyDetails(item);
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

        assertThat(adapter.findAllSpacesWithStatus(
                Set.of(),
                NOW
        )).isEmpty();

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
        List<ActiveOccupancyView> occupancies =
                includeIncorrectOccupancy
                        ? List.of(occupancy(
                                1L,
                                NOW.plusMinutes(30).toOffsetDateTime()
                        ))
                        : List.of();
        stubSpaces(List.of(space), occupancies);

        SpaceListResult item = findOnlyItem();

        assertThat(item.operationalStatus())
                .isEqualTo(operationalStatus);
        assertThat(item.status())
                .isEqualTo(SpaceUsageStatus.NOT_APPLICABLE);
        assertThat(item.occupiedBySameCohort()).isFalse();
        assertThat(item.occupancyExpiresAt()).isNull();
        assertThat(item.remainingTimeSeconds()).isNull();
        assertNoOccupancyDetails(item);
    }

    private void assertNoOccupancyDetails(SpaceListResult item) {
        assertThat(item.occupancyCohortId()).isNull();
        assertThat(item.occupierMembershipId()).isNull();
        assertThat(item.occupierUserId()).isNull();
        assertThat(item.participantUserIds()).isNull();
    }

    private void stubSpaces(
            List<SpaceJpaEntity> spaces,
            List<ActiveOccupancyView> occupancies
    ) {
        when(spaceRepository.findAllByDeletedAtIsNullOrderByIdAsc())
                .thenReturn(spaces);
        when(roomOccupancyRepository.findAllActiveBySpaceIds(
                spaces.stream().map(SpaceJpaEntity::getId).toList(),
                NOW.toOffsetDateTime()
        )).thenReturn(occupancies);
        if (!occupancies.isEmpty()) {
            lenient().when(roomOccupancyRepository.findAllActiveParticipants(
                    occupancies.stream()
                            .map(ActiveOccupancyView::getId)
                            .toList()
            )).thenReturn(List.of());
        }
    }

    private SpaceListResult findOnlyItem() {
        List<SpaceListResult> items =
                adapter.findAllSpacesWithStatus(Set.of(), NOW);
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

    private ActiveOccupancyView occupancy(
            Long spaceId,
            OffsetDateTime expiresAt
    ) {
        ActiveOccupancyView occupancy = mock(ActiveOccupancyView.class);
        lenient().when(occupancy.getId()).thenReturn(spaceId + 100L);
        when(occupancy.getSpaceId()).thenReturn(spaceId);
        lenient().when(occupancy.getExpiresAt())
                .thenReturn(expiresAt.toInstant());
        lenient().when(occupancy.getOccupierCohortId()).thenReturn(21L);
        return occupancy;
    }
}
