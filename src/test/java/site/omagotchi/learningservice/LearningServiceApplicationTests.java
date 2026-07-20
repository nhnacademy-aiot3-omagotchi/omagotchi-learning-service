package site.omagotchi.learningservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
				WHERE version = '1'
				  AND success
				""", Integer.class);

		assertThat(cohortsTable).isEqualTo("learning_service.cohorts");
		assertThat(appliedMigrationCount).isOne();
	}

}
