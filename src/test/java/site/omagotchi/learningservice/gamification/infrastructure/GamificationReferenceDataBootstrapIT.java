package site.omagotchi.learningservice.gamification.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@Import({
        TestcontainersConfiguration.class,
        GamificationReferenceDataBootstrap.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("게이미피케이션 기준 데이터 부트스트랩")
class GamificationReferenceDataBootstrapIT {

    private static final int CHARACTER_COUNT = 8;
    private static final int QUEST_COUNT = 5;
    private static final int LEVEL_COUNT = 30;

    @Autowired
    private GamificationReferenceDataBootstrap bootstrap;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("캐릭터·퀘스트·레벨 기준 데이터를 적재한다")
    void seedsReferenceData() {
        bootstrap.run(null);

        assertThat(count("game_characters")).isEqualTo(CHARACTER_COUNT);
        assertThat(count("quest_templates")).isEqualTo(QUEST_COUNT);
        assertThat(count("level_policies")).isEqualTo(LEVEL_COUNT);
    }

    // 롤링 배포로 인스턴스가 여러 번 기동해도 행이 늘어나면 안 된다.
    @Test
    @DisplayName("여러 번 실행해도 행 수가 늘지 않는다")
    void isIdempotent() {
        bootstrap.run(null);
        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(count("game_characters")).isEqualTo(CHARACTER_COUNT);
        assertThat(count("quest_templates")).isEqualTo(QUEST_COUNT);
        assertThat(count("level_policies")).isEqualTo(LEVEL_COUNT);
    }

    @Test
    @DisplayName("값이 어긋난 캐릭터를 기준 값으로 복구한다")
    void restoresDriftedCharacter() {
        jdbcTemplate.update("""
                UPDATE learning_service.game_characters
                SET name = '깨진이', description = '깨진 설명'
                WHERE code = 'COMMIT'
                """);

        bootstrap.run(null);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT name FROM learning_service.game_characters WHERE code = 'COMMIT'
                """, String.class)).isEqualTo("커밋이");
        assertThat(count("game_characters")).isEqualTo(CHARACTER_COUNT);
    }

    @Test
    @DisplayName("레벨 곡선은 (N-1)^2 * 100 을 따른다")
    void appliesLevelCurve() {
        bootstrap.run(null);

        assertThat(minTotalXpOf(1)).isZero();
        assertThat(minTotalXpOf(2)).isEqualTo(100L);
        assertThat(minTotalXpOf(10)).isEqualTo(8_100L);
        assertThat(minTotalXpOf(30)).isEqualTo(84_100L);
    }

    // 사용자가 선택한 캐릭터가 사라지면 안 되므로 부트스트랩은 DELETE 하지 않는다.
    @Test
    @DisplayName("기준 목록에 없는 캐릭터를 삭제하지 않는다")
    void keepsUnlistedCharacter() {
        jdbcTemplate.update("""
                INSERT INTO learning_service.game_characters (name, code, description, asset_key)
                VALUES ('레거시', 'LEGACY', '과거에 운영하던 캐릭터입니다.', 'legacy')
                """);

        bootstrap.run(null);

        assertThat(count("game_characters")).isEqualTo(CHARACTER_COUNT + 1);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM learning_service." + table, Integer.class);
        return count == null ? 0 : count;
    }

    private long minTotalXpOf(int level) {
        Long minTotalXp = jdbcTemplate.queryForObject("""
                SELECT min_total_xp FROM learning_service.level_policies WHERE level = ?
                """, Long.class, level);
        return minTotalXp == null ? -1L : minTotalXp;
    }
}
