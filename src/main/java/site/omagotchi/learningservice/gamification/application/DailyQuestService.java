package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.result.DailyQuestResult;
import site.omagotchi.learningservice.gamification.application.result.HomeResult;
import site.omagotchi.learningservice.gamification.domain.QuestStatus;
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
import java.util.UUID;

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
        return progressToday(userId, STUDY_COMPLETED_CODE);
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
        return completeToday(userId, LLM_QUEST_CODE);
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
            // 지난 날짜 보상은 수령 불가. 상태 전환은 expirePastQuests가 담당한다.
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

    @Transactional
    public void expirePastQuests() {
        LocalDate today = dateTimeProvider.currentAggregationDate();
        userDailyQuestRepository.findByQuestDateBefore(today)
                .forEach(UserDailyQuest::expire);
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
        // 같은 날짜 슬롯이 이미 있으면 기본 5개를 다시 만들지 않음
        if (userDailyQuestRepository.existsByUserIdAndQuestDate(userId, questDate)) {
            return;
        }
        List<UserDailyQuest> quests = dailyTemplates().stream()
                .map(template -> UserDailyQuest.fromTemplate(userId, questDate, template))
                .toList();
        userDailyQuestRepository.saveAll(quests);
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
