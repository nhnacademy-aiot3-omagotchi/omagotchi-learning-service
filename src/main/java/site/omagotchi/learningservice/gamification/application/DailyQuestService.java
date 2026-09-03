package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.port.DailyQuestIssueRepository;
import site.omagotchi.learningservice.gamification.application.result.DailyQuestResult;
import site.omagotchi.learningservice.gamification.application.result.HomeResult;
import site.omagotchi.learningservice.gamification.domain.QuestStatus;
import site.omagotchi.learningservice.gamification.domain.StudyTimeQuestTarget;
import site.omagotchi.learningservice.gamification.domain.StudyTimeQuestTitle;
import site.omagotchi.learningservice.gamification.domain.QuestTemplate;
import site.omagotchi.learningservice.gamification.domain.QuestType;
import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;
import site.omagotchi.learningservice.gamification.domain.XpSourceType;
import site.omagotchi.learningservice.gamification.infrastructure.QuestTemplateRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserDailyQuestRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.util.DateTimeProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyQuestService {

    public static final String ATTENDANCE_CODE = "ATTENDANCE";
    public static final String STUDY_COMPLETED_CODE = "STUDY_COMPLETED";
    public static final String CHARACTER_CHECKED_CODE = "CHARACTER_CHECKED";
    public static final String ROUTINE_REVIEW_CODE = "ROUTINE_REVIEW";
    public static final String LLM_QUEST_CODE = "LLM_QUEST";

    private final UserDailyQuestRepository userDailyQuestRepository;
    private final QuestTemplateRepository questTemplateRepository;
    private final XpRewardService xpRewardService;
    private final CharacterGrowthService characterGrowthService;
    private final DateTimeProvider dateTimeProvider;
    private final DailyQuestIssueRepository dailyQuestIssueRepository;
    private final StudyTimeQuestTargetResolver studyTimeQuestTargetResolver;
    private final UserStudySecondsReader userStudySecondsReader;

    @Transactional
    public List<DailyQuestResult> getOrCreateDailyQuests(UUID userId) {
        LocalDate questDate = dateTimeProvider.currentAggregationDate();
        createDailyQuestsIfAbsent(userId, questDate);
        return findDailyQuests(userId, questDate);
    }

    @Transactional
    public HomeResult getHome(UUID userId) {
        return new HomeResult(
                characterGrowthService.getGrowth(userId),
                getOrCreateDailyQuests(userId)
        );
    }

    @Transactional
    public DailyQuestResult handleAttendance(UUID userId) {
        return progressToday(userId, ATTENDANCE_CODE);
    }

    @Transactional
    public DailyQuestResult handleStudyCompleted(UUID userId) {
        LocalDate questDate = dateTimeProvider.currentAggregationDate();
        createDailyQuestsIfAbsent(userId, questDate);

        // "학습 완료하기"는 원래 계약대로 학습 1회에 완료한다.
        UserDailyQuest routine = requireTodayQuest(userId, questDate, STUDY_COMPLETED_CODE);
        routine.progress(1, dateTimeProvider.currentInstant());

        // 같은 학습 이벤트로 LLM 슬롯의 공부 시간 퀘스트도 판정한다.
        // 이벤트 횟수가 아니라 오늘 누적 공부시간을 보며, 기록 수정·삭제로 누적이 줄 수 있으므로
        // 증분이 아니라 매번 원본을 다시 읽는다. 행이 없거나 시간형이 아니면 건너뛴다.
        userDailyQuestRepository.findByUserIdAndQuestDateAndCode(userId, questDate, LLM_QUEST_CODE)
                .filter(UserDailyQuest::isStudyTimeQuest)
                .ifPresent(quest -> completeIfStudyTimeReached(userId, questDate, quest));

        return DailyQuestResult.from(routine);
    }

    private void completeIfStudyTimeReached(UUID userId, LocalDate questDate, UserDailyQuest quest) {
        Optional<Long> cohortId = userStudySecondsReader.findActiveCohortId(userId);
        if (cohortId.isEmpty()) {
            return;
        }
        long studySeconds = userStudySecondsReader.dailyStudySeconds(userId, cohortId.get(), questDate);
        if (studySeconds >= quest.getTargetSeconds()) {
            quest.complete(dateTimeProvider.currentInstant());
        }
    }

    @Transactional
    public DailyQuestResult handleCharacterChecked(UUID userId) {
        return progressToday(userId, CHARACTER_CHECKED_CODE);
    }

    @Transactional
    public DailyQuestResult handleRoutineReviewed(UUID userId) {
        return progressToday(userId, ROUTINE_REVIEW_CODE);
    }

    @Transactional
    public DailyQuestResult handleLlmQuestCompleted(UUID userId) {
        LocalDate questDate = dateTimeProvider.currentAggregationDate();
        createDailyQuestsIfAbsent(userId, questDate);
        UserDailyQuest quest = requireTodayQuest(userId, questDate, LLM_QUEST_CODE);

        // 예측 기반 LLM 슬롯은 학습 종료 이벤트 자체가 아니라 누적 공부시간으로
        // 완료한다. 기존 횟수형 LLM 퀘스트는 이전 이벤트 계약대로 완료 처리한다.
        if (quest.isStudyTimeQuest()) {
            completeIfStudyTimeReached(userId, questDate, quest);
        } else {
            quest.complete(dateTimeProvider.currentInstant());
        }
        return DailyQuestResult.from(quest);
    }

    @Transactional
    public DailyQuestResult claim(UUID userId, Long userDailyQuestId) {
        // 보상 수령과 상태 변경이 겹치지 않도록 퀘스트 행을 먼저 잠금
        UserDailyQuest quest = userDailyQuestRepository.findWithLockById(userDailyQuestId)
                .orElseThrow(() -> new BusinessException(GamificationErrorCode.DAILY_QUEST_NOT_FOUND));
        if (!quest.getUserId().equals(userId)) {
            throw new BusinessException(GamificationErrorCode.DAILY_QUEST_NOT_FOUND);
        }
        LocalDate today = dateTimeProvider.currentAggregationDate();
        if (quest.getQuestDate().isBefore(today)) {
            // 과거 퀘스트는 저장된 상태와 무관하게 보상을 수령할 수 없다.
            throw new BusinessException(GamificationErrorCode.DAILY_QUEST_EXPIRED);
        }
        if (quest.getStatus() == QuestStatus.CLAIMED) {
            throw new BusinessException(GamificationErrorCode.DAILY_QUEST_ALREADY_CLAIMED);
        }
        if (quest.getStatus() != QuestStatus.COMPLETED) {
            throw new BusinessException(GamificationErrorCode.DAILY_QUEST_NOT_COMPLETED);
        }

        quest.claim(dateTimeProvider.currentInstant());
        // 수령 상태 변경, 원장 생성, EXP 지급이 같은 트랜잭션에서 끝나야 함
        xpRewardService.reward(
                userId,
                quest.getRewardXp(),
                XpSourceType.DAILY_QUEST,
                quest.getId()
        );
        return DailyQuestResult.from(quest);
    }

    private List<DailyQuestResult> findDailyQuests(UUID userId, LocalDate questDate) {
        return userDailyQuestRepository.findByUserIdAndQuestDateOrderByIdAsc(userId, questDate).stream()
                .map(DailyQuestResult::from)
                .toList();
    }

    private DailyQuestResult progressToday(UUID userId, String code) {
        LocalDate questDate = dateTimeProvider.currentAggregationDate();
        createDailyQuestsIfAbsent(userId, questDate);
        UserDailyQuest quest = requireTodayQuest(userId, questDate, code);
        quest.progress(1, dateTimeProvider.currentInstant());
        return DailyQuestResult.from(quest);
    }

    private DailyQuestResult completeToday(UUID userId, String code) {
        LocalDate questDate = dateTimeProvider.currentAggregationDate();
        createDailyQuestsIfAbsent(userId, questDate);
        UserDailyQuest quest = requireTodayQuest(userId, questDate, code);
        quest.complete(dateTimeProvider.currentInstant());
        return DailyQuestResult.from(quest);
    }

    private UserDailyQuest requireTodayQuest(UUID userId, LocalDate questDate, String code) {
        return userDailyQuestRepository.findByUserIdAndQuestDateAndCode(userId, questDate, code)
                .orElseThrow(() -> new BusinessException(GamificationErrorCode.DAILY_QUEST_NOT_FOUND));
    }

    private void createDailyQuestsIfAbsent(UUID userId, LocalDate questDate) {
        // 날짜 단위로 검사하면 발급 시점이 둘로 갈릴 때 먼저 만들어진 한 건 때문에
        // 나머지 슬롯이 영영 생기지 않는다. 그래서 code 단위로 비교하되,
        // code마다 exists를 던지지 않도록 오늘 행을 한 번만 읽어 메모리에서 대조한다.
        Set<String> existingCodes = userDailyQuestRepository
                .findByUserIdAndQuestDateOrderByIdAsc(userId, questDate).stream()
                .map(UserDailyQuest::getCode)
                .collect(Collectors.toSet());

        List<UserDailyQuest> quests = dailyTemplates().stream()
                .filter(template -> !existingCodes.contains(template.getCode()))
                .map(template -> newQuest(userId, questDate, template))
                .toList();
        // 미리 걸러도 두 요청이 같은 순간에 "없음"으로 읽을 수 있다.
        // 남은 충돌은 저장소가 ON CONFLICT로 흡수한다.
        dailyQuestIssueRepository.issueIfAbsent(quests);
    }

    private UserDailyQuest newQuest(UUID userId, LocalDate questDate, QuestTemplate template) {
        // LLM 슬롯이 예측 기반 공부 시간 퀘스트 자리다. 나머지는 템플릿을 그대로 복사한다.
        if (!LLM_QUEST_CODE.equals(template.getCode())) {
            return UserDailyQuest.fromTemplate(userId, questDate, template);
        }
        // 목표는 사용자마다 다르므로 제목도 발급 시점에 만들어 행으로 복사한다.
        StudyTimeQuestTarget target = studyTimeQuestTargetResolver.resolve(userId, questDate);
        return UserDailyQuest.studyTimeFromTemplate(
                userId,
                questDate,
                template,
                StudyTimeQuestTitle.of(target.targetSeconds()),
                target
        );
    }

    private List<QuestTemplate> dailyTemplates() {
        List<QuestTemplate> activeTemplates = questTemplateRepository.findByActiveTrueOrderByDisplayOrderAsc();
        if (activeTemplates.isEmpty()) {
            // 운영 템플릿이 비어도 오늘 퀘스트 화면은 기본 슬롯으로 유지
            return defaultTemplates();
        }

        List<QuestTemplate> routineTemplates = activeTemplates.stream()
                .filter(template -> template.getType() == QuestType.ROUTINE)
                .limit(4)
                .toList();
        List<QuestTemplate> llmTemplates = activeTemplates.stream()
                .filter(template -> template.getType() == QuestType.LLM)
                .limit(1)
                .toList();

        List<QuestTemplate> selected = new ArrayList<>();
        selected.addAll(routineTemplates);
        selected.addAll(llmTemplates);
        return selected.stream()
                .sorted(Comparator.comparingInt(QuestTemplate::getDisplayOrder))
                .toList();
    }

    private List<QuestTemplate> defaultTemplates() {
        return List.of(
                QuestTemplate.create(QuestType.ROUTINE, ATTENDANCE_CODE, "출석하기", 1, 20, 1),
                QuestTemplate.create(QuestType.ROUTINE, STUDY_COMPLETED_CODE, "학습 완료하기", 1, 30, 2),
                QuestTemplate.create(QuestType.ROUTINE, CHARACTER_CHECKED_CODE, "캐릭터 확인하기", 1, 10, 3),
                QuestTemplate.create(QuestType.ROUTINE, ROUTINE_REVIEW_CODE, "오늘 학습 돌아보기", 1, 20, 4),
                QuestTemplate.create(QuestType.LLM, LLM_QUEST_CODE, "AI 추천 퀘스트", 1, 40, 5)
        );
    }
}
