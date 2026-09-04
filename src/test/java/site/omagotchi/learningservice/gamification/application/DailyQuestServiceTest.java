package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.gamification.application.port.DailyQuestIssueRepository;
import site.omagotchi.learningservice.gamification.domain.QuestStatus;
import site.omagotchi.learningservice.gamification.domain.QuestTemplate;
import site.omagotchi.learningservice.gamification.domain.StudyTimeQuestTarget;
import site.omagotchi.learningservice.gamification.domain.QuestType;
import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;
import site.omagotchi.learningservice.gamification.domain.XpSourceType;
import site.omagotchi.learningservice.gamification.infrastructure.QuestTemplateRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserDailyQuestRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.util.DateTimeProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("일일 퀘스트 서비스")
class DailyQuestServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T05:00:00Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long COHORT_ID = 7L;
    private static final int TARGET_SECONDS = 15_840; // 4h * 1.1

    @Mock
    private UserDailyQuestRepository userDailyQuestRepository;

    @Mock
    private QuestTemplateRepository questTemplateRepository;

    @Mock
    private XpRewardService xpRewardService;

    @Mock
    private CharacterGrowthService characterGrowthService;

    @Mock
    private DailyQuestIssueRepository dailyQuestIssueRepository;

    @Mock
    private StudyTimeQuestTargetResolver studyTimeQuestTargetResolver;

    @Mock
    private UserStudySecondsReader userStudySecondsReader;

    private DailyQuestService dailyQuestService;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        DateTimeProvider dateTimeProvider = new DateTimeProvider(Clock.fixed(NOW, ZoneOffset.UTC));
        today = dateTimeProvider.currentAggregationDate();
        dailyQuestService = new DailyQuestService(
                userDailyQuestRepository,
                questTemplateRepository,
                xpRewardService,
                characterGrowthService,
                dateTimeProvider,
                dailyQuestIssueRepository,
                studyTimeQuestTargetResolver,
                userStudySecondsReader
        );
    }

    @Test
    @DisplayName("이미 오늘 퀘스트가 있으면 중복 생성하지 않는다")
    void doesNotCreateDuplicatedDailyQuests(CapturedOutput output) {
        when(userDailyQuestRepository.findByUserIdAndQuestDateOrderByIdAsc(USER_ID, today))
                .thenReturn(allDefaultQuests());

        dailyQuestService.getOrCreateDailyQuests(USER_ID);

        verify(dailyQuestIssueRepository).issueIfAbsent(List.of());
        assertThat(output.getOut()).doesNotContain("일일 퀘스트 발급을 시도했습니다.");
    }

    @Test
    @DisplayName("발급 후보가 있으면 실제 삽입 건수와 함께 기록한다")
    void logsDailyQuestIssueResult(CapturedOutput output) {
        when(userDailyQuestRepository.findByUserIdAndQuestDateOrderByIdAsc(USER_ID, today))
                .thenReturn(List.of());
        when(studyTimeQuestTargetResolver.resolve(USER_ID, today))
                .thenReturn(StudyTimeQuestTarget.model(TARGET_SECONDS, "study-time-test"));
        when(dailyQuestIssueRepository.issueIfAbsent(any())).thenReturn(5);

        dailyQuestService.getOrCreateDailyQuests(USER_ID);

        assertThat(output.getOut())
                .contains("일일 퀘스트 발급을 시도했습니다.")
                .contains("userIdMasked=00000000, questDate=2026-08-05")
                .contains("candidateCount=5, insertedCount=5")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("보상을 중복 수령할 수 없다")
    void rejectsDuplicatedClaim() {
        UserDailyQuest quest = completedQuest(today);
        quest.claim(NOW);
        ReflectionTestUtils.setField(quest, "id", 1L);
        when(userDailyQuestRepository.findWithLockById(1L)).thenReturn(Optional.of(quest));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> dailyQuestService.claim(USER_ID, 1L)
        );

        assertEquals(GamificationErrorCode.DAILY_QUEST_ALREADY_CLAIMED, exception.getErrorCode());
    }

    @Test
    @DisplayName("지난 날짜의 미수령 보상은 받을 수 없다")
    void rejectsPastQuestClaim() {
        UserDailyQuest quest = completedQuest(today.minusDays(1));
        ReflectionTestUtils.setField(quest, "id", 1L);
        when(userDailyQuestRepository.findWithLockById(1L)).thenReturn(Optional.of(quest));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> dailyQuestService.claim(USER_ID, 1L)
        );

        assertEquals(GamificationErrorCode.DAILY_QUEST_EXPIRED, exception.getErrorCode());
    }

    @Test
    @DisplayName("LLM 퀘스트 완료 이벤트는 LLM 슬롯을 완료 처리한다")
    void completesLlmQuestFromEvent() {
        UserDailyQuest quest = UserDailyQuest.create(
                USER_ID,
                today,
                null,
                QuestType.LLM,
                DailyQuestService.LLM_QUEST_CODE,
                "AI 추천 퀘스트",
                1,
                40
        );
        when(userDailyQuestRepository.findByUserIdAndQuestDateOrderByIdAsc(USER_ID, today))
                .thenReturn(allDefaultQuests());
        when(userDailyQuestRepository.findByUserIdAndQuestDateAndCode(
                USER_ID,
                today,
                DailyQuestService.LLM_QUEST_CODE
        )).thenReturn(Optional.of(quest));

        var result = dailyQuestService.handleLlmQuestCompleted(USER_ID);

        assertEquals(QuestStatus.COMPLETED, result.status());
    }

    @Test
    @DisplayName("캐릭터 확인과 학습 돌아보기 행동은 해당 일일 퀘스트를 완료 처리한다")
    void completesUserActionQuests() {
        UserDailyQuest characterQuest = routineQuest(
                DailyQuestService.CHARACTER_CHECKED_CODE,
                "캐릭터 확인하기"
        );
        UserDailyQuest reviewQuest = routineQuest(
                DailyQuestService.ROUTINE_REVIEW_CODE,
                "오늘 학습 돌아보기"
        );
        when(userDailyQuestRepository.findByUserIdAndQuestDateOrderByIdAsc(USER_ID, today))
                .thenReturn(allDefaultQuests());
        when(userDailyQuestRepository.findByUserIdAndQuestDateAndCode(
                USER_ID,
                today,
                DailyQuestService.CHARACTER_CHECKED_CODE
        )).thenReturn(Optional.of(characterQuest));
        when(userDailyQuestRepository.findByUserIdAndQuestDateAndCode(
                USER_ID,
                today,
                DailyQuestService.ROUTINE_REVIEW_CODE
        )).thenReturn(Optional.of(reviewQuest));

        var characterResult = dailyQuestService.handleCharacterChecked(USER_ID);
        var reviewResult = dailyQuestService.handleRoutineReviewed(USER_ID);

        assertAll(
                () -> assertEquals(DailyQuestService.CHARACTER_CHECKED_CODE, characterResult.code()),
                () -> assertEquals(1, characterResult.progressCount()),
                () -> assertEquals(QuestStatus.COMPLETED, characterResult.status()),
                () -> assertEquals(DailyQuestService.ROUTINE_REVIEW_CODE, reviewResult.code()),
                () -> assertEquals(1, reviewResult.progressCount()),
                () -> assertEquals(QuestStatus.COMPLETED, reviewResult.status())
        );
        verify(userDailyQuestRepository).findByUserIdAndQuestDateAndCode(
                USER_ID,
                today,
                DailyQuestService.CHARACTER_CHECKED_CODE
        );
        verify(userDailyQuestRepository).findByUserIdAndQuestDateAndCode(
                USER_ID,
                today,
                DailyQuestService.ROUTINE_REVIEW_CODE
        );
    }

    @Test
    @DisplayName("완료된 퀘스트 보상은 DAILY_QUEST 원장으로 지급한다")
    void rewardsCompletedQuestByLedgerSource() {
        UserDailyQuest quest = completedQuest(today);
        ReflectionTestUtils.setField(quest, "id", 10L);
        when(userDailyQuestRepository.findWithLockById(10L)).thenReturn(Optional.of(quest));

        dailyQuestService.claim(USER_ID, 10L);

        verify(xpRewardService).reward(USER_ID, 20, XpSourceType.DAILY_QUEST, 10L);
        assertEquals(QuestStatus.CLAIMED, quest.getStatus());
    }

    @Test
    @DisplayName("학습 완료 이벤트는 학습 완료하기를 1회 진행하고, 누적 시간이 목표 미만이면 공부 시간 퀘스트를 완료하지 않는다")
    void progressesRoutineButKeepsStudyTimeQuestBelowTarget() {
        UserDailyQuest routine = routineQuest(DailyQuestService.STUDY_COMPLETED_CODE, "학습 완료하기");
        UserDailyQuest studyTime = studyTimeQuest();
        stubTodayQuests(routine, studyTime);
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.dailyStudySeconds(USER_ID, COHORT_ID, today))
                .thenReturn((long) TARGET_SECONDS - 1);

        var result = dailyQuestService.handleStudyCompleted(USER_ID);

        assertAll(
                () -> assertEquals(DailyQuestService.STUDY_COMPLETED_CODE, result.code()),
                () -> assertEquals(1, result.progressCount()),
                () -> assertEquals(QuestStatus.COMPLETED, result.status()),
                () -> assertEquals(QuestStatus.IN_PROGRESS, studyTime.getStatus()),
                () -> assertEquals(0, studyTime.getProgressCount())
        );
    }

    @Test
    @DisplayName("누적 시간이 목표 이상이면 공부 시간 퀘스트를 완료하고 학습 완료하기도 1회 진행한다")
    void completesStudyTimeQuestAtTarget() {
        UserDailyQuest routine = routineQuest(DailyQuestService.STUDY_COMPLETED_CODE, "학습 완료하기");
        UserDailyQuest studyTime = studyTimeQuest();
        stubTodayQuests(routine, studyTime);
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.dailyStudySeconds(USER_ID, COHORT_ID, today))
                .thenReturn((long) TARGET_SECONDS);

        var result = dailyQuestService.handleStudyCompleted(USER_ID);

        assertAll(
                () -> assertEquals(1, result.progressCount()),
                () -> assertEquals(QuestStatus.COMPLETED, result.status()),
                () -> assertEquals(QuestStatus.COMPLETED, studyTime.getStatus()),
                () -> assertEquals(1, studyTime.getProgressCount())
        );
    }

    @Test
    @DisplayName("활성 기수 소속이 없으면 누적 시간을 읽지 않고 공부 시간 퀘스트를 그대로 둔다")
    void skipsStudyTimeCheckWithoutActiveCohort() {
        UserDailyQuest routine = routineQuest(DailyQuestService.STUDY_COMPLETED_CODE, "학습 완료하기");
        UserDailyQuest studyTime = studyTimeQuest();
        stubTodayQuests(routine, studyTime);
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.empty());

        var result = dailyQuestService.handleStudyCompleted(USER_ID);

        assertAll(
                () -> assertEquals(QuestStatus.COMPLETED, result.status()),
                () -> assertEquals(QuestStatus.IN_PROGRESS, studyTime.getStatus())
        );
        verify(userStudySecondsReader, never()).dailyStudySeconds(any(), any(), any());
    }

    private void stubTodayQuests(UserDailyQuest routine, UserDailyQuest studyTime) {
        when(userDailyQuestRepository.findByUserIdAndQuestDateOrderByIdAsc(USER_ID, today))
                .thenReturn(allDefaultQuests());
        when(userDailyQuestRepository.findByUserIdAndQuestDateAndCode(
                USER_ID, today, DailyQuestService.STUDY_COMPLETED_CODE
        )).thenReturn(Optional.of(routine));
        when(userDailyQuestRepository.findByUserIdAndQuestDateAndCode(
                USER_ID, today, DailyQuestService.LLM_QUEST_CODE
        )).thenReturn(Optional.of(studyTime));
    }

    /** LLM 슬롯에 발급된 예측 기반 공부 시간 퀘스트. targetCount는 1이고 실제 목표는 targetSeconds다. */
    private UserDailyQuest studyTimeQuest() {
        QuestTemplate template = QuestTemplate.create(
                QuestType.LLM, DailyQuestService.LLM_QUEST_CODE, "AI 추천 퀘스트", 1, 40, 5
        );
        return UserDailyQuest.studyTimeFromTemplate(
                USER_ID, today, template, "오늘 4시간 24분 공부하기",
                StudyTimeQuestTarget.model(TARGET_SECONDS, "study-time-test")
        );
    }

    private UserDailyQuest completedQuest(LocalDate questDate) {
        UserDailyQuest quest = UserDailyQuest.create(
                USER_ID,
                questDate,
                null,
                QuestType.ROUTINE,
                DailyQuestService.ATTENDANCE_CODE,
                "출석하기",
                1,
                20
        );
        quest.complete(NOW);
        return quest;
    }

    private UserDailyQuest routineQuest(String code, String title) {
        return UserDailyQuest.create(
                USER_ID,
                today,
                null,
                QuestType.ROUTINE,
                code,
                title,
                1,
                10
        );
    }

    /**
     * 발급 가드가 code 단위로 바뀌었으므로 "오늘 퀘스트가 이미 있다"는 상태는
     * 기본 슬롯 5개가 모두 존재하는 것으로 표현한다.
     */
    private List<UserDailyQuest> allDefaultQuests() {
        return List.of(
                routineQuest(DailyQuestService.ATTENDANCE_CODE, "출석하기"),
                routineQuest(DailyQuestService.STUDY_COMPLETED_CODE, "학습 완료하기"),
                routineQuest(DailyQuestService.CHARACTER_CHECKED_CODE, "캐릭터 확인하기"),
                routineQuest(DailyQuestService.ROUTINE_REVIEW_CODE, "오늘 학습 돌아보기"),
                UserDailyQuest.create(
                        USER_ID,
                        today,
                        null,
                        QuestType.LLM,
                        DailyQuestService.LLM_QUEST_CODE,
                        "AI 추천 퀘스트",
                        1,
                        40
                )
        );
    }
}
