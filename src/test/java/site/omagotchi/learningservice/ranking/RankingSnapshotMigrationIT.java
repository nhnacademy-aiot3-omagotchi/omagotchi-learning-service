package site.omagotchi.learningservice.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("랭킹 스냅샷 제거 마이그레이션")
class RankingSnapshotMigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("스냅샷 테이블이 존재하지 않음")
    void dropsSnapshotTables() {
        String entriesTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('learning_service.ranking_snapshot_entries')::TEXT",
                String.class
        );
        String snapshotsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('learning_service.ranking_snapshots')::TEXT",
                String.class
        );

        assertAll(
                () -> assertNull(entriesTable),
                () -> assertNull(snapshotsTable)
        );
    }
}
