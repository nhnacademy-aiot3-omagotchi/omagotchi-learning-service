 package site.omagotchi.learningservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.application.command.ApproveMembershipCommand;
import site.omagotchi.learningservice.cohort.application.command.AssignCohortManagerCommand;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateCohortCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateJoinCommand;
import site.omagotchi.learningservice.cohort.application.command.IssueJoinCodeCommand;
import site.omagotchi.learningservice.cohort.application.command.SaveAttendancePolicyCommand;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.gamification.application.CharacterOnboardingService;
import site.omagotchi.learningservice.gamification.application.DailyQuestService;
import site.omagotchi.learningservice.gamification.application.command.CreateUserCharacterCommand;
import site.omagotchi.learningservice.gamification.application.result.DailyQuestResult;
import site.omagotchi.learningservice.gamification.domain.QuestStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.prediction.application.exception.PredictionClientException;
import site.omagotchi.learningservice.prediction.application.port.PredictionClient;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;
import site.omagotchi.learningservice.global.util.DateTimeProvider;
import site.omagotchi.learningservice.space.application.SpaceCommandService;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.study.application.StudyRecordCommandService;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static site.omagotchi.learningservice.global.time.AggregationDateTime.today;

/**
 * 예측 기반 학습 시간 퀘스트가 실제 발급 경로에서 제대로 나오는지 확인하는 회귀 테스트.
 *
 * <p>{@link PredictionClient}만 스텁하고 피처 조립·정책 계산·저장은 모두 실제 코드를 태운다.
 * 그래서 활성 소속·출결 정책·대표 캐릭터 같은 예측 전제 조건도 함께 검증된다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("예측 학습 시간 퀘스트 발급")
class PredictionQuestFlowIT {

    private static final UUID SYSTEM_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final int DEFAULT_TARGET_SECONDS = 12_600; // 하한 3h30m
    private static final int MAX_TARGET_SECONDS = 41_400; // 상한 11h30m

    @Autowired CohortService cohortService;
    @Autowired CohortManagerService cohortManagerService;
    @Autowired CohortAttendancePolicyService attendancePolicyService;
    @Autowired JoinCodeService joinCodeService;
    @Autowired CohortMembershipService membershipService;
    @Autowired CharacterOnboardingService characterOnboardingService;
    @Autowired DailyQuestService dailyQuestService;
    @Autowired SpaceCommandService spaceCommandService;
    @Autowired StudyRecordCommandService studyRecordCommandService;
    @Autowired DateTimeProvider dateTimeProvider;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean PredictionClient predictionClient;

    private UUID studentId;
    private UUID managerId;
    private Long cohortId;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        // 한 사람은 같은 운영 기간에 여러 기수의 관리자가 될 수 없으므로 테스트마다 새로 만든다.
        managerId = UUID.randomUUID();
        joinApprovedStudent();
    }

    @Test
    @DisplayName("예측값에 도전 계수를 적용한 목표로 발급된다")
    void issuesQuestFromPrediction() {
        // 4.0h * 1.1 = 4.4h = 15840초 = 4시간 24분
        when(predictionClient.predict(any(), any()))
                .thenReturn(new StudyTimePredictionResult(4.0, "study-time-test"));

        DailyQuestResult quest = issueAndFindStudyTimeQuest();

        assertThat(quest.title()).isEqualTo("오늘 4시간 24분 공부하기");
        assertThat(quest.targetCount()).isEqualTo(1);
        assertThat(quest.progressCount()).isZero();
        assertThat(quest.status()).isEqualTo(QuestStatus.IN_PROGRESS);
        assertThat(targetSecondsOf()).isEqualTo(15_840);
        assertThat(targetSourceOf()).isEqualTo("MODEL");
        assertThat(modelVersionOf()).isEqualTo("study-time-test");
    }

    @Test
    @DisplayName("모델 출력 상한에 계수를 곱해도 퀘스트 상한을 넘지 않는다")
    void clampsToQuestMaximum() {
        // 11.5h * 1.1 = 12.65h. 먼저 자르고 곱하면 상한이 상한 역할을 못 한다.
        when(predictionClient.predict(any(), any()))
                .thenReturn(new StudyTimePredictionResult(11.5, "study-time-test"));

        DailyQuestResult quest = issueAndFindStudyTimeQuest();

        assertThat(quest.title()).isEqualTo("오늘 11시간 30분 공부하기");
        assertThat(targetSecondsOf()).isEqualTo(MAX_TARGET_SECONDS);
    }

    @Test
    @DisplayName("예측이 0으로 나와도 하한 아래로 내려가지 않는다")
    void neverIssuesBelowMinimum() {
        // 0초 목표는 ck_user_daily_quests_target_count 위반이라 저장 자체가 실패한다.
        when(predictionClient.predict(any(), any()))
                .thenReturn(new StudyTimePredictionResult(0.0, "study-time-test"));

        DailyQuestResult quest = issueAndFindStudyTimeQuest();

        assertThat(quest.title()).isEqualTo("오늘 3시간 30분 공부하기");
        assertThat(targetSecondsOf()).isEqualTo(DEFAULT_TARGET_SECONDS);
    }

    @Test
    @DisplayName("prediction-service가 실패해도 퀘스트는 발급된다")
    void issuesQuestWhenPredictionFails() {
        // 예측 실패가 퀘스트 발급 자체를 막으면 안 된다(ADR prediction/0001).
        when(predictionClient.predict(any(), any()))
                .thenThrow(PredictionClientException.unavailable(new IllegalStateException("테스트 강제 실패")));

        List<DailyQuestResult> quests = dailyQuestService.getOrCreateDailyQuests(studentId);

        assertThat(quests).hasSize(5);
        DailyQuestResult quest = studyTimeQuestOf(quests);
        // 학습 이력이 없으므로 B2도 값을 내지 못해 기본값으로 내려간다.
        assertThat(quest.title()).isEqualTo("오늘 3시간 30분 공부하기");
        assertThat(targetSecondsOf()).isEqualTo(DEFAULT_TARGET_SECONDS);
        assertThat(targetSourceOf()).isEqualTo("DEFAULT");
        assertThat(modelVersionOf()).isNull();
    }

    @Test
    @DisplayName("두 번 조회해도 퀘스트가 중복 발급되지 않는다")
    void doesNotReissueOnSecondCall() {
        when(predictionClient.predict(any(), any()))
                .thenReturn(new StudyTimePredictionResult(4.0, "study-time-test"));

        List<DailyQuestResult> first = dailyQuestService.getOrCreateDailyQuests(studentId);
        List<DailyQuestResult> second = dailyQuestService.getOrCreateDailyQuests(studentId);

        assertThat(first).hasSize(5);
        assertThat(second).hasSize(5);
        assertThat(countQuestRows()).isEqualTo(5);
        // 목표는 발급 시점에 고정된다. 다시 조회한다고 다시 계산하지 않는다.
        assertThat(studyTimeQuestOf(second).title()).isEqualTo("오늘 4시간 24분 공부하기");
    }

    @Test
    @DisplayName("나머지 네 개는 기존 횟수형 계약을 유지한다")
    void keepsCountQuestsUnchanged() {
        when(predictionClient.predict(any(), any()))
                .thenReturn(new StudyTimePredictionResult(4.0, "study-time-test"));

        List<DailyQuestResult> quests = dailyQuestService.getOrCreateDailyQuests(studentId);

        assertThat(quests)
                .filteredOn(quest -> !DailyQuestService.LLM_QUEST_CODE.equals(quest.code()))
                .hasSize(4)
                .allSatisfy(quest -> {
                    assertThat(quest.targetCount()).isEqualTo(1);
                    assertThat(quest.progressCount()).isZero();
                });
        Integer countQuestTargets = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM learning_service.user_daily_quests
                WHERE user_id = ? AND code <> ? AND target_seconds IS NOT NULL
                """, Integer.class, studentId, DailyQuestService.LLM_QUEST_CODE);
        assertThat(countQuestTargets).isZero();
    }

    @Test
    @DisplayName("학습 기록이 목표에 못 미치면 학습 완료하기만 완료되고 공부 시간 퀘스트는 진행 중으로 남는다")
    void keepsStudyTimeQuestInProgressWhenStudySecondsBelowTarget() throws InterruptedException {
        when(predictionClient.predict(any(), any()))
                .thenReturn(new StudyTimePredictionResult(4.0, "study-time-test"));
        dailyQuestService.getOrCreateDailyQuests(studentId);

        // 집계일 경계(04:00 KST)를 넘지 않는 짧은 기록. 목표 4시간 24분에는 한참 못 미친다.
        // 수동 기록 정책(ManualStudyRecordPolicy)이 분 단위 정렬을 요구하므로 분으로 잘라낸다.
        Instant end = dateTimeProvider.currentInstant().truncatedTo(ChronoUnit.MINUTES);
        Instant boundary = dateTimeProvider.startOfAggregationDate(dateTimeProvider.currentAggregationDate());
        Instant start = end.minus(Duration.ofMinutes(10));
        if (start.isBefore(boundary)) {
            start = boundary;
        }
        // 집계일이 시작된 직후 1분 안에서는 길이 0인 기록밖에 못 만들어 전제 자체가 성립하지 않는다.
        assumeTrue(start.isBefore(end), "집계일 시작 직후에는 학습 기록을 만들 수 없어 건너뛴다.");
        studyRecordCommandService.create(studentId, cohortId, new CreateStudyRecordCommand(start, end));

        // 기록 저장이 STUDY_COMPLETED 이벤트를 발행하고 그 이벤트가 handleStudyCompleted를 부른다.
        // 여기서 직접 호출하면 비동기 처리와 같은 행을 동시에 갱신해 낙관적 잠금 충돌이 나므로
        // 운영과 같은 이벤트 경로가 끝나기를 기다린다.
        DailyQuestResult routine = waitForRoutineStudyQuestCompleted();

        assertThat(routine.progressCount()).isEqualTo(1);

        DailyQuestResult studyTime = issueAndFindStudyTimeQuest();
        assertThat(studyTime.status()).isEqualTo(QuestStatus.IN_PROGRESS);
        assertThat(studyTime.progressCount()).isZero();
    }

    private DailyQuestResult waitForRoutineStudyQuestCompleted() throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            DailyQuestResult quest = dailyQuestService.getOrCreateDailyQuests(studentId).stream()
                    .filter(candidate -> DailyQuestService.STUDY_COMPLETED_CODE.equals(candidate.code()))
                    .findFirst()
                    .orElseThrow();
            if (quest.status() == QuestStatus.COMPLETED) {
                return quest;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("학습 기록 이벤트가 학습 완료하기 퀘스트를 완료하지 못했습니다.");
    }

    private DailyQuestResult issueAndFindStudyTimeQuest() {
        return studyTimeQuestOf(dailyQuestService.getOrCreateDailyQuests(studentId));
    }

    private DailyQuestResult studyTimeQuestOf(List<DailyQuestResult> quests) {
        return quests.stream()
                .filter(quest -> DailyQuestService.LLM_QUEST_CODE.equals(quest.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LLM 슬롯의 공부 시간 퀘스트가 발급되지 않았습니다."));
    }

    private Integer targetSecondsOf() {
        return selectStudyTimeColumn("target_seconds", Integer.class);
    }

    private String targetSourceOf() {
        return selectStudyTimeColumn("target_source", String.class);
    }

    private String modelVersionOf() {
        return selectStudyTimeColumn("model_version", String.class);
    }

    private <T> T selectStudyTimeColumn(String column, Class<T> type) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM learning_service.user_daily_quests"
                        + " WHERE user_id = ? AND code = ?",
                type, studentId, DailyQuestService.LLM_QUEST_CODE
        );
    }

    private Integer countQuestRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM learning_service.user_daily_quests WHERE user_id = ?",
                Integer.class, studentId
        );
    }

    private void joinApprovedStudent() {
        LocalDate today = today();
        var cohort = cohortService.create(
                new CreateCohortCommand(
                        "예측퀘스트-" + UUID.randomUUID(),
                        "예측 퀘스트 회귀 검증",
                        today.minusDays(1),
                        today.plusMonths(1)
                ),
                SYSTEM_ADMIN_ID,
                GlobalRole.SYSTEM_ADMIN
        );
        cohortId = cohort.id();
        cohortManagerService.assignManager(
                cohort.id(), new AssignCohortManagerCommand(managerId), SYSTEM_ADMIN_ID, GlobalRole.SYSTEM_ADMIN
        );
        // 예측 피처 조립이 출결 정책을 요구하므로 반드시 있어야 한다.
        attendancePolicyService.savePolicy(
                cohort.id(),
                new SaveAttendancePolicyCommand("Asia/Seoul", LocalTime.of(9, 0), LocalTime.of(18, 0),
                        LocalTime.of(10, 0), 30),
                managerId,
                GlobalRole.USER
        );
        // 기수를 ACTIVE로 바꾸려면 활성 실습실이 최소 1개 있어야 한다.
        // Space 피처의 규칙을 우회하지 않도록 public application method로 준비한다.
        Space lab = spaceCommandService.create(
                new CreateSpaceCommand("예측퀘스트-실습실-" + cohort.id(), SpaceType.LAB, 20, cohort.id()),
                managerId
        );
        spaceCommandService.activate(lab.getId(), managerId);
        cohortService.changeStatus(cohort.id(), new ChangeCohortStatusCommand(CohortStatus.ACTIVE), GlobalRole.SYSTEM_ADMIN);

        var joinCode = joinCodeService.issue(
                cohort.id(), new IssueJoinCodeCommand(OffsetDateTime.now().plusDays(1)), managerId
        );
        var application = membershipService.join(new CreateJoinCommand(joinCode.code()), studentId);
        membershipService.approve(
                application.id(), new ApproveMembershipCommand(CohortMembershipRole.STUDENT), managerId, GlobalRole.USER
        );
        // 대표 캐릭터 레벨은 예측의 필수 피처다. 없으면 예측이 하드 실패한다.
        // 닉네임은 전역 유니크라 테스트마다 달라야 한다. 규칙은 한글·영숫자 2~12자.
        String nickname = "오마" + studentId.toString().replace("-", "").substring(0, 8);
        characterOnboardingService.createRepresentativeCharacter(
                studentId, new CreateUserCharacterCommand(1L, nickname, "pistachio")
        );
    }
}
