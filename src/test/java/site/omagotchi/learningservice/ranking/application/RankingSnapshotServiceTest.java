package site.omagotchi.learningservice.ranking.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.ranking.application.port.RankingSnapshotPort;
import site.omagotchi.learningservice.ranking.application.port.StudyTimeRankingQueryPort;
import site.omagotchi.learningservice.ranking.application.result.StudyTimeRankingResult;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshotEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("랭킹 스냅샷 서비스")
class RankingSnapshotServiceTest {

    @Mock
    private RankingSnapshotPort rankingSnapshotPort;

    @Mock
    private StudyTimeRankingQueryPort studyTimeRankingQueryPort;

    @Mock
    private CharacterGrowthService characterGrowthService;

    @Test
    @DisplayName("같은 기간 snapshot은 다시 만들지 않는다")
    void reusesExistingSnapshot() {
        RankingSnapshot snapshot = snapshot(1L);
        when(rankingSnapshotPort.insertIfAbsent(
                1L,
                RankingPeriod.DAILY,
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5)
        )).thenReturn(0);
        when(rankingSnapshotPort.findByCohortIdAndPeriodAndBaseDate(
                1L,
                RankingPeriod.DAILY,
                LocalDate.of(2026, 8, 5)
        )).thenReturn(Optional.of(snapshot));
        RankingSnapshotService service = service();

        RankingSnapshot result = service.getOrCreate(1L, RankingPeriod.DAILY, LocalDate.of(2026, 8, 5));

        assertEquals(1L, result.getId());
        verify(studyTimeRankingQueryPort, never()).findStudySeconds(any(), any(), any());
        verify(rankingSnapshotPort, never()).saveEntries(any());
    }

    @Test
    @DisplayName("대표 캐릭터가 있는 사용자만 snapshot entry로 저장한다")
    void storesRepresentativeCharacterEntriesOnly() {
        UUID rankedUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID userWithoutCharacterId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        RankingSnapshot snapshot = snapshot(1L);
        RepresentativeCharacterResult character = new RepresentativeCharacterResult(rankedUserId, 10L, "야간반장");
        when(rankingSnapshotPort.insertIfAbsent(
                1L,
                RankingPeriod.DAILY,
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5)
        )).thenReturn(1);
        when(rankingSnapshotPort.findByCohortIdAndPeriodAndBaseDate(
                1L,
                RankingPeriod.DAILY,
                LocalDate.of(2026, 8, 5)
        )).thenReturn(Optional.of(snapshot));
        when(studyTimeRankingQueryPort.findStudySeconds(
                1L,
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5)
        )).thenReturn(List.of(
                studyRow(rankedUserId, 28_800),
                studyRow(userWithoutCharacterId, 21_600)
        ));
        when(characterGrowthService.findRepresentativeCharacters(any())).thenReturn(List.of(character));
        RankingSnapshotService service = service();

        service.getOrCreate(1L, RankingPeriod.DAILY, LocalDate.of(2026, 8, 5));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RankingSnapshotEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(rankingSnapshotPort).saveEntries(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(10L, captor.getValue().getFirst().getUserCharacterId());
    }

    private RankingSnapshotService service() {
        return new RankingSnapshotService(
                rankingSnapshotPort,
                studyTimeRankingQueryPort,
                characterGrowthService
        );
    }

    private StudyTimeRankingResult studyRow(UUID userId, long studySeconds) {
        return new StudyTimeRankingResult(userId, studySeconds);
    }

    private RankingSnapshot snapshot(Long id) {
        RankingSnapshot snapshot = RankingSnapshot.create(
                1L,
                RankingPeriod.DAILY,
                LocalDate.of(2026, 8, 5),
                site.omagotchi.learningservice.ranking.domain.RankingDateRange.from(
                        RankingPeriod.DAILY,
                        LocalDate.of(2026, 8, 5)
                )
        );
        ReflectionTestUtils.setField(snapshot, "id", id);
        ReflectionTestUtils.setField(snapshot, "generatedAt", java.time.Instant.parse("2026-08-05T00:00:00Z"));
        return snapshot;
    }
}
