package site.omagotchi.learningservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.gamification.application.GamificationEventType;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventReceiptRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class LearningServiceApplicationIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private GamificationEventReceiptRepository gamificationEventReceiptRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void usesProjectPostgreSqlVersion() {
		String serverVersion = jdbcTemplate.queryForObject(
				"SELECT current_setting('server_version')",
				String.class
		);

		assertThat(serverVersion).startsWith("18.1");
	}

	@Test
	void appliesInitialFlywayMigration() {
		String cohortsTable = jdbcTemplate.queryForObject(
				"SELECT to_regclass('learning_service.cohorts')::text",
				String.class
		);
		Integer appliedMigrationCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM learning_service.flyway_schema_history
				WHERE version IN ('1', '2')
				  AND success
				""", Integer.class);
		List<String> userIdColumnTypes = jdbcTemplate.queryForList("""
				SELECT data_type
				FROM information_schema.columns
				WHERE table_schema = 'learning_service'
				  AND (column_name = 'user_id' OR column_name LIKE '%_by_user_id')
				ORDER BY table_name, column_name
				""", String.class);

		assertThat(cohortsTable).isEqualTo("learning_service.cohorts");
		assertThat(appliedMigrationCount).isEqualTo(2);
		assertThat(userIdColumnTypes)
				.isNotEmpty()
				.allMatch("uuid"::equals);
	}

	@Test
	void appliesFrontendCharacterAssetMigration() {
		Integer characterCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM learning_service.game_characters",
				Integer.class
		);
		List<String> assetKeys = jdbcTemplate.queryForList("""
				SELECT asset_key
				FROM learning_service.game_characters
				ORDER BY asset_key
				""", String.class);
		String defaultColor = jdbcTemplate.queryForObject("""
				SELECT column_default
				FROM information_schema.columns
				WHERE table_schema = 'learning_service'
				  AND table_name = 'user_characters'
				  AND column_name = 'color_id'
				""", String.class);
		Integer migrationCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM learning_service.flyway_schema_history
				WHERE version = '9'
				  AND success
				""", Integer.class);

		assertThat(characterCount).isEqualTo(8);
		assertThat(assetKeys).containsExactly(
				"caffeine",
				"commit",
				"debug",
				"kid",
				"night",
				"server",
				"sprout",
				"study"
		);
		assertThat(defaultColor).contains("original");
		assertThat(migrationCount).isEqualTo(1);
	}

	@Test
	void appliesGamificationEventReceiptMigrationAndClaimsOnce() {
		UUID userId = UUID.randomUUID();
		String sourceId = UUID.randomUUID().toString();
		Instant occurredAt = Instant.parse("2026-08-20T00:00:00Z");

		boolean firstClaim = gamificationEventReceiptRepository.claim(
				GamificationEventType.STUDY_COMPLETED,
				sourceId,
				userId,
				occurredAt
		);
		boolean duplicateClaim = gamificationEventReceiptRepository.claim(
				GamificationEventType.STUDY_COMPLETED,
				sourceId,
				userId,
				occurredAt
		);
		Integer migrationCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM learning_service.flyway_schema_history
				WHERE version = '10'
				  AND success
				""", Integer.class);

		assertThat(firstClaim).isTrue();
		assertThat(duplicateClaim).isFalse();
		assertThat(migrationCount).isEqualTo(1);
	}

	@Test
	void installsBtreeGistInLearningServiceSchema() {
		String extensionSchema = jdbcTemplate.queryForObject("""
				SELECT namespace.nspname
				FROM pg_extension extension
				JOIN pg_namespace namespace ON namespace.oid = extension.extnamespace
				WHERE extension.extname = 'btree_gist'
				""", String.class);

		assertThat(extensionSchema).isEqualTo("learning_service");
	}

}
