package site.omagotchi.learningservice.ranking.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.global.util.DateTimeProvider;
import site.omagotchi.learningservice.ranking.application.port.RankingSnapshotPort;
import site.omagotchi.learningservice.ranking.domain.RankingDateRange;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingRankedEntry;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshotEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("랭킹 조회 서비스")
class RankingQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Mock
    private RankingSnapshotService rankingSnapshotService;

    @Mock
    private RankingSnapshotPort rankingSnapshotPort;

    @Mock
    private CharacterGrowthService characterGrowthService;

    @Mock
    private DateTimeProvider dateTimeProvider;

    @Test
    @DisplayName("top10, myRank, generatedAt을 함께 반환한다")
    void returnsTop10AndMyRank() {
        RankingSnapshot snapshot = snapshot();
        RepresentativeCharacterResult myCharacter = new RepresentativeCharacterResult(USER_ID, 11L, "나");
        List<RankingSnapshotEntry> entries = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(rank -> entry(rank, rank == 11 ? USER_ID : UUID.randomUUID()))
                .toList();
        when(rankingSnapshotService.getOrCreate(1L, RankingPeriod.DAILY, LocalDate.of(2026, 8, 5)))
                .thenReturn(snapshot);
        when(rankingSnapshotPort.findEntries(1L))
                .thenReturn(entries);
        when(characterGrowthService.findRepresentativeCharacter(USER_ID))
                .thenReturn(Optional.of(myCharacter));
        when(rankingSnapshotPort.findEntry(1L, 11L))
                .thenReturn(Optional.of(entries.get(10)));
        RankingQueryService service = new RankingQueryService(
                rankingSnapshotService,
                rankingSnapshotPort,
                characterGrowthService,
                dateTimeProvider
        );

        var result = service.getStudyRanking(USER_ID, 1L, RankingPeriod.DAILY, LocalDate.of(2026, 8, 5));

        assertAll(
                () -> assertEquals(10, result.top10().size()),
                () -> assertEquals(11, result.myRank().rank()),
                () -> assertNotNull(result.generatedAt())
        );
    }

    private RankingSnapshot snapshot() {
        RankingSnapshot snapshot = RankingSnapshot.create(
                1L,
                RankingPeriod.DAILY,
                LocalDate.of(2026, 8, 5),
                RankingDateRange.from(RankingPeriod.DAILY, LocalDate.of(2026, 8, 5))
        );
        ReflectionTestUtils.setField(snapshot, "id", 1L);
        ReflectionTestUtils.setField(snapshot, "generatedAt", Instant.parse("2026-08-05T00:00:00Z"));
        return snapshot;
    }

    private RankingSnapshotEntry entry(int rank, UUID userId) {
        return RankingSnapshotEntry.from(1L, new RankingRankedEntry(
                rank,
                userId,
                (long) rank,
                "캐릭터" + rank,
                30_000L - rank
        ));
    }
}
