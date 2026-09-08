package site.omagotchi.learningservice.cohort.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;

import java.time.LocalTime;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code TIME} 컬럼이 JVM 기본 시간대와 무관하게 벽시계 값 그대로 오가는지 고정한다.
 *
 * <p>이 검증이 없으면 {@code hibernate.jdbc.time_zone: UTC}와 {@code java.sql.Time} 변환이
 * 맞물려 값이 서버 시간대만큼 이동하는데, 쓰기와 읽기가 같은 폭으로 어긋나 앱 안에서는
 * 정상으로 보인다. DB 실제 값을 함께 확인해야만 드러난다.</p>
 *
 * <p>기존 행은 UTC 서버가 쓴 행과 raw SQL로 넣은 행이 같은 모양이므로, raw INSERT를 기존
 * 데이터로 사용한다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("출결 정책 TIME 매핑")
class CohortAttendancePolicyTimeMappingIT {

    private static final UUID ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final LocalTime END = LocalTime.of(18, 0);
    private static final LocalTime CUTOFF = LocalTime.of(10, 0);

    @Autowired
    private CohortAttendancePolicyRepository policyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TimeZone originalTimeZone;

    @AfterEach
    void restoreTimeZone() {
        if (originalTimeZone != null) {
            TimeZone.setDefault(originalTimeZone);
            originalTimeZone = null;
        }
    }

    @Test
    @DisplayName("기존 행을 저장된 벽시계 시각 그대로 읽는다")
    void readsExistingRowAsStoredWallClock() {
        useTimeZone("Asia/Seoul");
        Long cohortId = saveCohort();
        insertPolicy(cohortId, "09:00", "18:00", "10:00");

        CohortAttendancePolicy policy = policyRepository.findById(cohortId).orElseThrow();

        assertThat(policy.getScheduledStartTime()).isEqualTo(START);
        assertThat(policy.getScheduledEndTime()).isEqualTo(END);
        assertThat(policy.getAbsenceCutoffTime()).isEqualTo(CUTOFF);
    }

    @Test
    @DisplayName("새로 저장한 값이 DB에 벽시계 그대로 들어간다")
    void storesNewRowAsWallClock() {
        useTimeZone("Asia/Seoul");
        Long cohortId = saveCohort();

        policyRepository.saveAndFlush(CohortAttendancePolicy.create(
                cohortId, "Asia/Seoul", START, END, CUTOFF, 30, ADMIN_ID
        ));

        assertThat(rawTimes(cohortId)).isEqualTo("09:00:00 / 18:00:00 / 10:00:00");
    }

    @Test
    @DisplayName("absence_cutoff_time의 NULL을 양방향으로 보존한다")
    void preservesNullAbsenceCutoff() {
        useTimeZone("Asia/Seoul");
        Long readCohortId = saveCohort();
        insertPolicy(readCohortId, "09:00", "18:00", null);

        CohortAttendancePolicy read = policyRepository.findById(readCohortId).orElseThrow();
        assertThat(read.getAbsenceCutoffTime()).isNull();

        Long writeCohortId = saveCohort();
        policyRepository.saveAndFlush(CohortAttendancePolicy.create(
                writeCohortId, "Asia/Seoul", START, END, null, 30, ADMIN_ID
        ));

        assertThat(absenceCutoffIsNull(writeCohortId)).isTrue();
    }

    /**
     * 매핑을 되돌리면(= JVM 시간대 변환이 다시 끼면) 이 검증이 깨진다. 두 시간대에서 같은
     * 저장값이 나오는지가 되돌림 여부를 판별하는 기준이다.
     */
    @Test
    @DisplayName("JVM 기본 시간대가 달라도 저장값과 조회값이 같다")
    void isIndependentOfJvmDefaultTimeZone() {
        useTimeZone("UTC");
        Long utcCohortId = saveCohort();
        policyRepository.saveAndFlush(CohortAttendancePolicy.create(
                utcCohortId, "Asia/Seoul", START, END, CUTOFF, 30, ADMIN_ID
        ));
        String utcStored = rawTimes(utcCohortId);

        useTimeZone("Asia/Seoul");
        Long seoulCohortId = saveCohort();
        policyRepository.saveAndFlush(CohortAttendancePolicy.create(
                seoulCohortId, "Asia/Seoul", START, END, CUTOFF, 30, ADMIN_ID
        ));

        assertThat(rawTimes(seoulCohortId)).isEqualTo(utcStored);
        assertThat(policyRepository.findById(utcCohortId).orElseThrow().getScheduledEndTime())
                .isEqualTo(END);
    }

    private void useTimeZone(String zoneId) {
        if (originalTimeZone == null) {
            originalTimeZone = TimeZone.getDefault();
        }
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
    }

    private Long saveCohort() {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.cohorts (
                            name, description, start_date, end_date, status, created_by_user_id
                        ) values (?, '시간대 매핑', '2026-09-01', '2026-09-30', 'ACTIVE', ?)
                        returning id
                        """,
                Long.class,
                "TIME매핑-" + UUID.randomUUID(),
                ADMIN_ID
        );
    }

    private void insertPolicy(Long cohortId, String start, String end, String cutoff) {
        jdbcTemplate.update("""
                        insert into learning_service.cohort_attendance_policies (
                            cohort_id, timezone, scheduled_start_time, scheduled_end_time,
                            absence_cutoff_time, allowed_away_minutes, updated_by_user_id
                        ) values (?, 'Asia/Seoul', ?::time, ?::time, ?::time, 30, ?)
                        """,
                cohortId, start, end, cutoff, ADMIN_ID
        );
    }

    private String rawTimes(Long cohortId) {
        return jdbcTemplate.queryForObject("""
                select scheduled_start_time::text || ' / ' || scheduled_end_time::text
                       || ' / ' || absence_cutoff_time::text
                  from learning_service.cohort_attendance_policies
                 where cohort_id = ?
                """, String.class, cohortId);
    }

    private boolean absenceCutoffIsNull(Long cohortId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select absence_cutoff_time is null
                  from learning_service.cohort_attendance_policies
                 where cohort_id = ?
                """, Boolean.class, cohortId));
    }
}
