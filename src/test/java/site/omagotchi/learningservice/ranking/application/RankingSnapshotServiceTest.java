package site.omagotchi.learningservice.ranking.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.ranking.domain.RankingPeriod;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshot;
import site.omagotchi.learningservice.ranking.domain.RankingSnapshotEntry;
import site.omagotchi.learningservice.ranking.infrastructure.RankingSnapshotEntryRepository;
import site.omagotchi.learningservice.ranking.infrastructure.RankingSnapshotRepository;
import site.omagotchi.learningservice.ranking.infrastructure.StudyTimeRankingRow;
import site.omagotchi.learningservice.ranking.infrastructure.StudyTimeRankingRepository;

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
    private RankingSnapshotRepository rankingSnapshotRepository;

    @Mock
    private RankingSnapshotEntryRepository rankingSnapshotEntryRepository;

    @Mock
    private StudyTimeRankingRepository studyTimeRankingRepository;

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Test
    @DisplayName("같은 기간 snapshot은 다시 만들지 않는다")
    void reusesExistingSnapshot() {
        RankingSnapshot snapshot = snapshot(1L);
        when(rankingSnapshotRepository.insertIfAbsent(
                1L,
                RankingPeriod.DAILY.name(),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5)
        )).thenReturn(0);
        when(rankingSnapshotRepository.findByCohortIdAndPeriodAndBaseDate(
                1L,
                RankingPeriod.DAILY,
                LocalDate.of(2026, 8, 5)
        )).thenReturn(Optional.of(snapshot));
        RankingSnapshotService service = service();

        RankingSnapshot result = service.getOrCreate(1L, RankingPeriod.DAILY, LocalDate.of(2026, 8, 5));

        assertEquals(1L, result.getId());
        verify(studyTimeRankingRepository, never()).findStudySeconds(any(), any(), any());
        verify(rankingSnapshotEntryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("대표 캐릭터가 있는 사용자만 snapshot entry로 저장한다")
    void storesRepresentativeCharacterEntriesOnly() {
        UUID rankedUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID userWithoutCharacterId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        RankingSnapshot snapshot = snapshot(1L);
        UserCharacter character = UserCharacter.representative(rankedUserId, 1L, "야간반장");
        ReflectionTestUtils.setField(character, "id", 10L);
        when(rankingSnapshotRepository.insertIfAbsent(
                1L,
                RankingPeriod.DAILY.name(),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5)
        )).thenReturn(1);
        when(rankingSnapshotRepository.findByCohortIdAndPeriodAndBaseDate(
                1L,
                RankingPeriod.DAILY,
                LocalDate.of(2026, 8, 5)
        )).thenReturn(Optional.of(snapshot));
        when(studyTimeRankingRepository.findStudySeconds(
                1L,
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 5)
        )).thenReturn(List.of(
                studyRow(rankedUserId, 28_800),
                studyRow(userWithoutCharacterId, 21_600)
        ));
        when(userCharacterRepository.findByUserIdInAndRepresentativeTrue(any())).thenReturn(List.of(character));
        RankingSnapshotService service = service();

        service.getOrCreate(1L, RankingPeriod.DAILY, LocalDate.of(2026, 8, 5));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RankingSnapshotEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(rankingSnapshotEntryRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(10L, captor.getValue().getFirst().getUserCharacterId());
    }

    private RankingSnapshotService service() {
        return new RankingSnapshotService(
                rankingSnapshotRepository,
                rankingSnapshotEntryRepository,
                studyTimeRankingRepository,
                userCharacterRepository
        );
    }

    private StudyTimeRankingRow studyRow(UUID userId, long studySeconds) {
        return new StudyTimeRankingRow() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public long getStudySeconds() {
                return studySeconds;
            }
        };
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
