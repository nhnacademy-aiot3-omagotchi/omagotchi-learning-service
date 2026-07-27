package site.omagotchi.learningservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.application.port.in.CreateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.DeleteSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.in.UpdateSpaceUseCase;
import site.omagotchi.learningservice.space.application.port.out.SpaceRepository;
import site.omagotchi.learningservice.space.application.query.SpaceQueryService;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.exception.DuplicateSpaceNameException;
import site.omagotchi.learningservice.space.domain.exception.SpaceErrorCode;

import java.util.Map;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class LearningServiceApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CreateSpaceUseCase createSpaceUseCase;

	@Autowired
	private UpdateSpaceUseCase updateSpaceUseCase;

	@Autowired
	private DeleteSpaceUseCase deleteSpaceUseCase;

	@Autowired
	private SpaceQueryService spaceQueryService;

	@Autowired
	private SpaceRepository spaceRepository;

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
	void appliesSpaceTeamMigrations() {
		Map<String, Object> column = jdbcTemplate.queryForMap("""
				SELECT data_type, is_nullable
				FROM information_schema.columns
				WHERE table_schema = 'learning_service'
				  AND table_name = 'spaces'
				  AND column_name = 'cohort_id'
				""");
		Map<String, Object> foreignKey = jdbcTemplate.queryForMap("""
				SELECT ccu.table_schema AS referenced_schema,
				       ccu.table_name AS referenced_table,
				       ccu.column_name AS referenced_column,
				       rc.delete_rule
				FROM information_schema.table_constraints tc
				JOIN information_schema.referential_constraints rc
				  ON rc.constraint_catalog = tc.constraint_catalog
				 AND rc.constraint_schema = tc.constraint_schema
				 AND rc.constraint_name = tc.constraint_name
				JOIN information_schema.constraint_column_usage ccu
				  ON ccu.constraint_catalog = rc.unique_constraint_catalog
				 AND ccu.constraint_schema = rc.unique_constraint_schema
				 AND ccu.constraint_name = rc.unique_constraint_name
				WHERE tc.constraint_schema = 'learning_service'
				  AND tc.table_name = 'spaces'
				  AND tc.constraint_name = 'fk_spaces_cohort'
				""");
		Integer migrationCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM learning_service.flyway_schema_history
				WHERE version = '5'
				  AND success
				""", Integer.class);
		Integer indexCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM pg_indexes
				WHERE schemaname = 'learning_service'
				  AND tablename = 'spaces'
				  AND indexname = 'ix_spaces_lab_cohort'
				""", Integer.class);

		assertThat(column)
				.containsEntry("data_type", "bigint")
				.containsEntry("is_nullable", "YES");
		assertThat(foreignKey)
				.containsEntry("referenced_schema", "learning_service")
				.containsEntry("referenced_table", "cohorts")
				.containsEntry("referenced_column", "id")
				.containsEntry("delete_rule", "SET NULL");
		assertThat(migrationCount).isOne();
		assertThat(indexCount).isOne();
	}

	@Test
	void rejectsUnknownCohortId() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO learning_service.spaces (
				    name, space_type, capacity, cohort_id
				) VALUES (?, 'LAB', 20, ?)
				""", "존재하지 않는 기수 FK 테스트 공간", Long.MAX_VALUE))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void clearsManagementCohortWhenReferencedCohortIsDeleted() {
		Long cohortId = insertCohort("FK SET NULL 테스트 기수");
		jdbcTemplate.update("""
				INSERT INTO learning_service.spaces (
				    name, space_type, capacity, cohort_id
				) VALUES (?, 'LAB', 20, ?)
				""", "FK SET NULL 테스트 공간", cohortId);

		jdbcTemplate.update(
				"DELETE FROM learning_service.cohorts WHERE id = ?",
				cohortId
		);

		Long storedCohortId = jdbcTemplate.queryForObject("""
				SELECT cohort_id
				FROM learning_service.spaces
				WHERE name = 'FK SET NULL 테스트 공간'
				""", Long.class);
		assertThat(storedCohortId).isNull();
	}

	@Test
	void createsSpaceWithoutCohortAndKeepsItInactive() {
		Space created = createSpaceUseCase.create(
				new CreateSpaceCommand(
						"신규 공간 회귀 테스트",
						SpaceType.MEETING,
						8
				)
		);
		Map<String, Object> stored = jdbcTemplate.queryForMap("""
				SELECT cohort_id, status
				FROM learning_service.spaces
				WHERE id = ?
				""", created.getId());

		assertThat(created.getCohortId()).isNull();
		assertThat(created.getOperationalStatus())
				.isEqualTo(SpaceOperationalStatus.INACTIVE);
		assertThat(stored.get("cohort_id")).isNull();
		assertThat(stored.get("status")).isEqualTo("INACTIVE");
	}

	@Test
	void preservesCohortIdThroughQueryUpdateAndDelete() {
		Long cohortId = insertCohort("기존 기능 회귀 테스트 기수");
		Long spaceId = jdbcTemplate.queryForObject("""
				INSERT INTO learning_service.spaces (
				    name, space_type, capacity, cohort_id
				) VALUES (?, 'LAB', 20, ?)
				RETURNING id
				""", Long.class, "기존 기능 회귀 테스트 공간", cohortId);

		assertThat(spaceQueryService.getSpaceList())
				.extracting(item -> item.spaceId())
				.contains(spaceId);

		Space updated = updateSpaceUseCase.update(
				spaceId,
				new UpdateSpaceCommand(
						"기존 기능 회귀 테스트 공간 수정",
						SpaceType.STUDY,
						24
				)
		);

		assertThat(updated.getCohortId()).isEqualTo(cohortId);
		assertThat(readCohortId(spaceId)).isEqualTo(cohortId);

		deleteSpaceUseCase.delete(spaceId);

		assertThat(readCohortId(spaceId)).isEqualTo(cohortId);
		assertThat(spaceQueryService.getSpaceList())
				.extracting(item -> item.spaceId())
				.doesNotContain(spaceId);
	}

	@Test
	void translatesSpaceNameUniqueIndexViolation() {
		Space first = Space.create(
				"  DB 유니크 충돌 테스트 공간  ",
				SpaceType.MEETING,
				8,
				java.time.ZonedDateTime.now()
		);
		Space duplicate = Space.create(
				"db 유니크 충돌 테스트 공간",
				SpaceType.MEETING,
				8,
				java.time.ZonedDateTime.now()
		);
		spaceRepository.save(first);

		assertThatThrownBy(() -> spaceRepository.save(duplicate))
				.isInstanceOf(DuplicateSpaceNameException.class)
				.satisfies(exception -> assertThat(
						((DuplicateSpaceNameException) exception)
								.getErrorCode()
				).isEqualTo(SpaceErrorCode.DUPLICATE_NAME));
	}

	@Test
	void allowsReusingNameOfSoftDeletedSpace() {
		Space first = Space.create(
				"삭제 후 재사용 테스트 공간",
				SpaceType.MEETING,
				8,
				java.time.ZonedDateTime.now()
		);
		Space saved = spaceRepository.save(first);
		spaceRepository.save(saved.delete(
				java.time.ZonedDateTime.now()
		));

		Space recreated = spaceRepository.save(Space.create(
				" 삭제 후 재사용 테스트 공간 ",
				SpaceType.MEETING,
				8,
				java.time.ZonedDateTime.now()
		));

		assertThat(recreated.getId()).isNotEqualTo(saved.getId());
		assertThat(recreated.getName())
				.isEqualTo("삭제 후 재사용 테스트 공간");
	}

	private Long insertCohort(String name) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO learning_service.cohorts (
				    name,
				    start_date,
				    end_date,
				    created_by_user_id
				) VALUES (
				    ?,
				    DATE '2026-01-01',
				    DATE '2026-12-31',
				    UUID '00000000-0000-0000-0000-000000000001'
				)
				RETURNING id
				""", Long.class, name);
	}

	private Long readCohortId(Long spaceId) {
		return jdbcTemplate.queryForObject(
				"SELECT cohort_id FROM learning_service.spaces WHERE id = ?",
				Long.class,
				spaceId
		);
	}

}
