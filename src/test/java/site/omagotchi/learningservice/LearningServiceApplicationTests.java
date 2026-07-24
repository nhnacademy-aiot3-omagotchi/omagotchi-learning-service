package site.omagotchi.learningservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class LearningServiceApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

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

}
