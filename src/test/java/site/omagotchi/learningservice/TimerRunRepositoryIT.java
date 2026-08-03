package site.omagotchi.learningservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.global.config.JpaAuditingConfig;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.port.TimerRunRepository;
import site.omagotchi.learningservice.study.domain.TimerEndReason;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.TimerRunJpaPersistence;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.TimerRunQueryDslRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        JpaAuditingConfig.class,
        TimerRunJpaPersistence.class,
        TimerRunQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("타이머 실행 저장소")
class TimerRunRepositoryIT {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2000-01-01T01:00:00Z");
    private static final TimerTimePolicy TIME_POLICY = new TimerTimePolicy(
            Duration.ofHours(12)
    );

    @Autowired
    private TimerRunRepository timerRunRepository;

    @Autowired
    private TimerRunQueryRepository timerRunQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("실행 생명주기")
    class Lifecycle {

        @Test
        @DisplayName("생성부터 종료까지 정상 처리")
        void persistsActiveRunAndExcludesItFromActiveQueryAfterEnd() {
            TimerRun created = timerRunRepository.create(
                    TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT)
            );

            TimerRun active = timerRunQueryRepository
                    .findActiveByCohortMembershipId(COHORT_MEMBERSHIP_ID)
                    .orElseThrow();

            assertAll(
                    () -> assertNotNull(active.getId()),
                    () -> assertEquals(created.getId(), active.getId()),
                    () -> assertEquals(STARTED_AT, active.getStartedAt()),
                    () -> assertNotNull(active.getCreatedAt()),
                    () -> assertTrue(active.isRunning())
            );

            active.stopOrExpire(ENDED_AT, TIME_POLICY);
            timerRunRepository.end(active);

            TimerRun ended = timerRunQueryRepository
                    .findOwnedById(active.getId(), COHORT_MEMBERSHIP_ID)
                    .orElseThrow();

            assertAll(
                    () -> assertTrue(
                            timerRunQueryRepository
                                    .findActiveByCohortMembershipId(COHORT_MEMBERSHIP_ID)
                                    .isEmpty()
                    ),
                    () -> assertFalse(ended.isRunning()),
                    () -> assertEquals(ENDED_AT, ended.getEndedAt()),
                    () -> assertEquals(3_600L, ended.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.STOP, ended.getEndReason()),
                    () -> assertEquals(active.getCreatedAt(), ended.getCreatedAt())
            );
        }
    }

    @Nested
    @DisplayName("시간 정밀도 제약")
    class TimePrecisionConstraint {

        @Test
        @DisplayName("초 미만 정밀도 저장 거절")
        void rejectsSubSecondPrecision() {
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update("""
                                    INSERT INTO learning_service.timer_runs (
                                        id,
                                        cohort_membership_id,
                                        started_at
                                    ) VALUES (?, ?, ?)
                                    """,
                            UUID.fromString("00000000-0000-0000-0000-000000000102"),
                            102L,
                            OffsetDateTime.parse("2000-01-01T00:00:00.001Z")
                    )
            );
        }
    }
}
