package site.omagotchi.learningservice.space.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.space.infrastructure.persistence.repository.SpringDataSpaceRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SpaceNameJpaQueryReader}의 소프트 삭제 필터 (알림 문구 fallback 회귀 방지).
 *
 * <p>삭제된 공간의 이름을 그대로 돌려주면 {@code VacancyAlertDispatcher}·
 * {@code OccupancyExpiryReminder}의 "이름 조회 실패 → 공간 {id}로 대체" fallback이
 * 걸리지 않아, 이미 사라진 방 이름이 그대로 사용자 알림에 노출된다.</p>
 */
@Import({TestcontainersConfiguration.class, QueryDslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("공간 이름 조회 (소프트 삭제 제외)")
class SpaceNameJpaQueryReaderIT {

    @Autowired
    private SpringDataSpaceRepository springDataSpaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SpaceNameJpaQueryReader reader;

    @BeforeEach
    void setUp() {
        reader = new SpaceNameJpaQueryReader(springDataSpaceRepository);
    }

    @Test
    @DisplayName("삭제되지 않은 공간은 이름을 돌려준다.")
    void returnsNameForActiveSpace() {
        Long spaceId = insertSpace("이름조회-활성", null);

        assertThat(reader.findName(spaceId)).contains("이름조회-활성");
    }

    @Test
    @DisplayName("소프트 삭제된 공간은 빈 값을 돌려준다 — 옛 이름이 알림에 노출되면 안 된다.")
    void returnsEmptyForDeletedSpace() {
        Long spaceId = insertSpace("이름조회-삭제", OffsetDateTime.now());

        Optional<String> name = reader.findName(spaceId);

        assertThat(name).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 공간은 빈 값을 돌려준다.")
    void returnsEmptyForMissingSpace() {
        assertThat(reader.findName(999_999L)).isEmpty();
    }

    private Long insertSpace(String name, OffsetDateTime deletedAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.spaces (name, space_type, deleted_at)
                VALUES (?, 'MEETING', ?)
                RETURNING id
                """, Long.class, name, deletedAt);
    }
}
