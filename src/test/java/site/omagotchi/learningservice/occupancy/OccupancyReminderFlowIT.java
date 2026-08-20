package site.omagotchi.learningservice.occupancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.occupancy.application.OccupancyExpiryReminder;
import site.omagotchi.learningservice.occupancy.application.OccupancyParticipantService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyLifecycleService;
import site.omagotchi.learningservice.occupancy.application.RoomOccupancyService;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #355의 연장·재알림·반납 백엔드 리허설.
 *
 * <p>Notification 운영 adapter 대신 테스트 mock으로 발송 횟수만 관찰한다. 점유와 참여자
 * 추가, 임박 알림 스캔, 연장, 반납은 모두 실제 application service와 PostgreSQL을 사용한다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class OccupancyReminderFlowIT {

    @Autowired
    private OccupancyTestFixture fixture;

    @Autowired
    private RoomOccupancyService roomOccupancyService;

    @Autowired
    private OccupancyParticipantService occupancyParticipantService;

    @Autowired
    private RoomOccupancyLifecycleService roomOccupancyLifecycleService;

    @Autowired
    private OccupancyExpiryReminder occupancyExpiryReminder;

    @Autowired
    private RoomOccupancyRepository occupancyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private OccupancyReminderSender reminderSender;

    private Instant currentInstant;

    @BeforeEach
    void setUpClock() {
        currentInstant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        when(clock.instant()).thenAnswer(invocation -> currentInstant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("연장으로 알림 기록을 초기화하면 새 만료 시각에 실제 재알림하고 반납한다.")
    void sendsAgainAfterExtensionAndCompletesReleaseFlow() {
        Long cohortId = fixture.createCohort("#355 연장 재알림 리허설");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        OccupancyTestFixture.Member participant = fixture.createActiveMember(cohortId);
        Long spaceId = fixture.createMeetingRoom(cohortId, "#355 연장 재알림 회의실", 8);

        RoomOccupancyResult started = roomOccupancyService.start(spaceId, occupier.userId());
        occupancyParticipantService.add(spaceId, participant.userId(), occupier.userId());
        assertThat(openParticipantRows(started.occupancyId())).isEqualTo(2);

        OffsetDateTime firstExpiresAt = now().plusMinutes(5);
        jdbcTemplate.update("""
                UPDATE learning_service.room_occupancies
                   SET expires_at = ?
                 WHERE id = ?
                """, firstExpiresAt, started.occupancyId());

        assertThat(roomOccupancyLifecycleService.sendExpiryReminders()).isEqualTo(1);
        OffsetDateTime firstReminderSentAt = reminderSentAt(started.occupancyId());
        assertThat(firstReminderSentAt).isNotNull();

        // 같은 만료 시각의 정상 커밋 경로는 다음 스캔에서 소진되어야 한다.
        assertThat(roomOccupancyLifecycleService.sendExpiryReminders()).isZero();
        verify(reminderSender, times(1)).sendExpiryReminder(
                org.mockito.ArgumentMatchers.any());

        RoomOccupancyResult extended =
                roomOccupancyLifecycleService.extend(spaceId, occupier.userId());
        assertThat(extended.expiresAt()).isEqualTo(firstExpiresAt.plusMinutes(30));
        assertThat(reminderSentAt(started.occupancyId())).isNull();

        // 실제 대기 대신 application Clock을 새 expiresAt의 5분 전으로 진행한다.
        currentInstant = extended.expiresAt().minusMinutes(5).toInstant();

        assertThat(roomOccupancyLifecycleService.sendExpiryReminders()).isEqualTo(1);
        OffsetDateTime secondReminderSentAt = reminderSentAt(started.occupancyId());
        assertThat(secondReminderSentAt).isAfter(firstReminderSentAt);

        // 두 번째 성공도 반복 스캔에서 다시 발송되지 않는다.
        assertThat(roomOccupancyLifecycleService.sendExpiryReminders()).isZero();
        ArgumentCaptor<OccupancyReminderSender.ExpiryReminder> reminders =
                ArgumentCaptor.forClass(OccupancyReminderSender.ExpiryReminder.class);
        verify(reminderSender, times(2)).sendExpiryReminder(reminders.capture());
        List<OccupancyReminderSender.ExpiryReminder> sent = reminders.getAllValues();
        assertThat(sent).extracting(OccupancyReminderSender.ExpiryReminder::occupancyId)
                .containsExactly(started.occupancyId(), started.occupancyId());
        assertThat(sent).extracting(OccupancyReminderSender.ExpiryReminder::expiresAt)
                .containsExactly(firstExpiresAt, extended.expiresAt());

        roomOccupancyLifecycleService.release(spaceId, occupier.userId());

        assertThat(occupancyStatus(started.occupancyId())).isEqualTo("RELEASED");
        assertThat(occupancyEndedAt(started.occupancyId())).isEqualTo(now());
        assertThat(openParticipantRows(started.occupancyId())).isZero();
    }

    @Test
    @DisplayName("두 실행이 같은 후보를 읽어도 행 락 재검증으로 한 번만 발송한다.")
    void concurrentReminderAttemptsSendOnlyOnce() throws Exception {
        Long cohortId = fixture.createCohort("#355 동시 알림 리허설");
        OccupancyTestFixture.Member occupier = fixture.createActiveMember(cohortId);
        Long spaceId = fixture.createMeetingRoom(cohortId, "#355 동시 알림 회의실", 8);
        RoomOccupancyResult started = roomOccupancyService.start(spaceId, occupier.userId());

        OffsetDateTime expiresAt = now().plusMinutes(5);
        jdbcTemplate.update("""
                UPDATE learning_service.room_occupancies
                   SET expires_at = ?
                 WHERE id = ?
                """, expiresAt, started.occupancyId());
        RoomOccupancyRepository.ExpiringOccupancy sameCandidate = occupancyRepository
                .findExpiringSoon(now(), now().plusMinutes(10)).stream()
                .filter(candidate -> candidate.occupancyId().equals(started.occupancyId()))
                .findFirst()
                .orElseThrow();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = List.of(
                    pool.submit(() -> sendAfterStart(ready, start, sameCandidate)),
                    pool.submit(() -> sendAfterStart(ready, start, sameCandidate))
            );
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Boolean firstResult = attempts.get(0).get(60, TimeUnit.SECONDS);
            Boolean secondResult = attempts.get(1).get(60, TimeUnit.SECONDS);
            assertThat(List.of(firstResult, secondResult))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        verify(reminderSender, times(1)).sendExpiryReminder(
                org.mockito.ArgumentMatchers.any());
        assertThat(reminderSentAt(started.occupancyId())).isNotNull();
    }

    private boolean sendAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            RoomOccupancyRepository.ExpiringOccupancy candidate
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return occupancyExpiryReminder.send(candidate, reminderSender);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(currentInstant, ZoneOffset.UTC);
    }

    private OffsetDateTime reminderSentAt(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT reminder_sent_at FROM learning_service.room_occupancies
                 WHERE id = ?
                """, OffsetDateTime.class, occupancyId);
    }

    private String occupancyStatus(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT status FROM learning_service.room_occupancies
                 WHERE id = ?
                """, String.class, occupancyId);
    }

    private OffsetDateTime occupancyEndedAt(Long occupancyId) {
        return jdbcTemplate.queryForObject("""
                SELECT ended_at FROM learning_service.room_occupancies
                 WHERE id = ?
                """, OffsetDateTime.class, occupancyId);
    }

    private int openParticipantRows(Long occupancyId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.occupancy_participants
                 WHERE occupancy_id = ? AND left_at IS NULL
                """, Integer.class, occupancyId);
        return count == null ? 0 : count;
    }
}
