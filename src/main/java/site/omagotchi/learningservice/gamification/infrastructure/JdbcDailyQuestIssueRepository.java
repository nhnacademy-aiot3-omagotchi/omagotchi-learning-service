package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.gamification.application.port.DailyQuestIssueRepository;
import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

/**
 * 중복을 흡수하는 일일 퀘스트 발급 저장소.
 *
 * <p>{@code ON CONFLICT DO NOTHING}으로 동시 발급 충돌을 예외 없이 넘긴다.
 * JPA {@code saveAll}은 IDENTITY 전략이라 persist 시점에 바로 INSERT가 나가고,
 * 거기서 제약 위반이 터지면 그 트랜잭션은 더 쓸 수 없어 catch로도 복구가 안 된다.
 * 같은 이유로 이 저장소는 gamification의 다른 멱등 저장 경로와 같은 방식을 쓴다.
 *
 * <p>{@code progress_count}·{@code status}·{@code created_at}·{@code updated_at}·{@code version}은
 * 테이블 DEFAULT를 그대로 쓴다. 값을 코드와 스키마 두 곳에 두지 않기 위해서다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcDailyQuestIssueRepository implements DailyQuestIssueRepository {

    private static final String INSERT_SQL = """
            INSERT INTO learning_service.user_daily_quests
                (user_id, quest_date, template_id, type, code, title,
                 target_count, reward_xp, target_seconds, target_source, model_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, quest_date, code) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int issueIfAbsent(List<UserDailyQuest> quests) {
        if (quests.isEmpty()) {
            return 0;
        }
        int[] inserted = jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                UserDailyQuest quest = quests.get(index);
                ps.setObject(1, quest.getUserId());
                ps.setDate(2, Date.valueOf(quest.getQuestDate()));
                ps.setObject(3, quest.getTemplateId(), Types.BIGINT);
                ps.setString(4, quest.getType().name());
                ps.setString(5, quest.getCode());
                ps.setString(6, quest.getTitle());
                ps.setInt(7, quest.getTargetCount());
                ps.setLong(8, quest.getRewardXp());
                ps.setObject(9, quest.getTargetSeconds(), Types.INTEGER);
                ps.setString(10, quest.getTargetSource().name());
                ps.setObject(11, quest.getModelVersion(), Types.VARCHAR);
            }

            @Override
            public int getBatchSize() {
                return quests.size();
            }
        });
        return Arrays.stream(inserted).sum();
    }
}
