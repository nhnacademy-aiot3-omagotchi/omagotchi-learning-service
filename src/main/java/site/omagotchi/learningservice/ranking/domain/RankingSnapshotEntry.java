package site.omagotchi.learningservice.ranking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "ranking_snapshot_entries", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingSnapshotEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private Long snapshotId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_character_id", nullable = false, updatable = false)
    private Long userCharacterId;

    @Column(name = "display_name", nullable = false, length = 30, updatable = false)
    private String displayName;

    @Column(name = "study_seconds", nullable = false, updatable = false)
    private long studySeconds;

    @Column(name = "rank_position", nullable = false, updatable = false)
    private int rank;

    public static RankingSnapshotEntry from(Long snapshotId, RankingRankedEntry entry) {
        RankingSnapshotEntry snapshotEntry = new RankingSnapshotEntry();
        snapshotEntry.snapshotId = snapshotId;
        snapshotEntry.userId = entry.userId();
        snapshotEntry.userCharacterId = entry.userCharacterId();
        snapshotEntry.displayName = entry.displayName();
        snapshotEntry.studySeconds = entry.studySeconds();
        snapshotEntry.rank = entry.rank();
        return snapshotEntry;
    }
}
