package site.omagotchi.learningservice.gamification.infrastructure;

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
import site.omagotchi.learningservice.gamification.domain.QuestTargetSource;
import site.omagotchi.learningservice.gamification.domain.QuestTemplate;
import site.omagotchi.learningservice.gamification.domain.QuestType;
import site.omagotchi.learningservice.gamification.domain.StudyTimeQuestTarget;
import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;
import site.omagotchi.learningservice.global.config.QueryDslConfig;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        JdbcDailyQuestIssueRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("일일 퀘스트 발급 저장소")
class JdbcDailyQuestIssueRepositoryIT {

    private static final LocalDate QUEST_DATE = LocalDate.of(2026, 8, 5);
    private static final String CODE = "STUDY_COMPLETED";

    @Autowired
    private JdbcDailyQuestIssueRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("같은 퀘스트를 두 번 발급해도 예외 없이 행 하나만 남는다")
    void absorbsDuplicateIssue() {
        // 두 요청이 같은 순간에 "오늘 것 없음"으로 읽으면 둘 다 같은 INSERT를 시도한다.
        // ON CONFLICT가 없으면 늦은 쪽이 uq_user_daily_quests_user_date_code 위반으로 실패한다.
        List<UserDailyQuest> quests = List.of(routineQuest());

        assertThat(repository.issueIfAbsent(quests)).isEqualTo(1);
        assertThatCode(() -> repository.issueIfAbsent(List.of(routineQuest())))
                .doesNotThrowAnyException();

        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("두 번째 발급은 삽입 건수 0을 돌려준다")
    void reportsZeroInsertedOnConflict() {
        repository.issueIfAbsent(List.of(routineQuest()));

        assertThat(repository.issueIfAbsent(List.of(routineQuest()))).isZero();
    }

    @Test
    @DisplayName("빈 목록은 저장을 시도하지 않는다")
    void skipsEmptyBatch() {
        assertThat(repository.issueIfAbsent(List.of())).isZero();
    }

    @Test
    @DisplayName("학습 시간 퀘스트는 목표 초와 출처를 함께 저장한다")
    void storesStudyTimeTargetColumns() {
        QuestTemplate template = QuestTemplate.create(QuestType.ROUTINE, CODE, "학습 완료하기", 1, 30, 2);
        UserDailyQuest quest = UserDailyQuest.studyTimeFromTemplate(
                userId,
                QUEST_DATE,
                template,
                "오늘 3시간 30분 공부하기",
                StudyTimeQuestTarget.model(12_600, "study-time-2026-08-16")
        );

        repository.issueIfAbsent(List.of(quest));

        assertThat(selectColumn("target_seconds", Integer.class)).isEqualTo(12_600);
        assertThat(selectColumn("target_source", String.class)).isEqualTo(QuestTargetSource.MODEL.name());
        assertThat(selectColumn("model_version", String.class)).isEqualTo("study-time-2026-08-16");
        assertThat(selectColumn("title", String.class)).isEqualTo("오늘 3시간 30분 공부하기");
        // DEFAULT로 채워지는 컬럼도 확인한다. 코드와 스키마 양쪽에 값을 두지 않기 위해 생략했다.
        assertThat(selectColumn("progress_count", Integer.class)).isZero();
        assertThat(selectColumn("status", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(selectColumn("version", Integer.class)).isZero();
    }

    private UserDailyQuest routineQuest() {
        return UserDailyQuest.create(
                userId, QUEST_DATE, null, QuestType.ROUTINE, CODE, "학습 완료하기", 1, 30
        );
    }

    private Integer countRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_service.user_daily_quests WHERE user_id = ?",
                Integer.class, userId
        );
    }

    private <T> T selectColumn(String column, Class<T> type) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM learning_service.user_daily_quests WHERE user_id = ?",
                type, userId
        );
    }
}
