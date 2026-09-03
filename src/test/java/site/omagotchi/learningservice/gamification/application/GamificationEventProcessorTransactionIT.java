package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.util.DateTimeProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

/** 실제 Spring 트랜잭션 프록시를 거친 이벤트 처리의 커밋·롤백 경계를 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class GamificationEventProcessorTransactionIT {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private GamificationEventProcessor eventProcessor;

    @Autowired
    private DateTimeProvider dateTimeProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserStudySecondsReader userStudySecondsReader;

    @MockitoSpyBean
    private DailyQuestService dailyQuestService;

    @BeforeEach
    void stubStudySecondsReader() {
        given(userStudySecondsReader.findActiveCohortId(any())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("이벤트 영수증과 퀘스트 변경은 하나의 트랜잭션으로 커밋된다")
    void commitsReceiptAndQuestChangesTogether() {
        UUID userId = UUID.randomUUID();
        String sourceId = UUID.randomUUID().toString();

        eventProcessor.process(studyCompletedEvent(userId, sourceId));

        assertThat(receiptCount(sourceId)).isEqualTo(1);
        assertThat(questCount(userId)).isEqualTo(5);
        assertThat(questStatus(userId, DailyQuestService.STUDY_COMPLETED_CODE))
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("LLM 퀘스트 처리 실패 시 영수증과 앞선 퀘스트 변경도 함께 롤백된다")
    void rollsBackReceiptAndQuestChangesWhenLlmQuestFails() {
        UUID userId = UUID.randomUUID();
        String sourceId = UUID.randomUUID().toString();
        willAnswer(invocation -> {
            invocation.callRealMethod();
            throw new ForcedFailure();
        }).given(dailyQuestService).handleLlmQuestCompleted(userId);

        assertThatThrownBy(() -> eventProcessor.process(studyCompletedEvent(userId, sourceId)))
                .isInstanceOf(ForcedFailure.class);

        assertThat(receiptCount(sourceId)).isZero();
        assertThat(questCount(userId)).isZero();
    }

    private GamificationEventMessage studyCompletedEvent(UUID userId, String sourceId) {
        return new GamificationEventMessage(
                GamificationEventType.STUDY_COMPLETED,
                sourceId,
                userId,
                OCCURRED_AT
        );
    }

    private int receiptCount(String sourceId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM learning_service.gamification_event_receipts
                WHERE event_type = 'STUDY_COMPLETED' AND source_id = ?
                """, Integer.class, sourceId);
        return count == null ? 0 : count;
    }

    private int questCount(UUID userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM learning_service.user_daily_quests
                WHERE user_id = ? AND quest_date = ?
                """, Integer.class, userId, aggregationDate());
        return count == null ? 0 : count;
    }

    private String questStatus(UUID userId, String code) {
        return jdbcTemplate.queryForObject("""
                SELECT status
                FROM learning_service.user_daily_quests
                WHERE user_id = ? AND quest_date = ? AND code = ?
                """, String.class, userId, aggregationDate(), code);
    }

    private LocalDate aggregationDate() {
        return dateTimeProvider.currentAggregationDate();
    }

    private static final class ForcedFailure extends RuntimeException {
    }
}
