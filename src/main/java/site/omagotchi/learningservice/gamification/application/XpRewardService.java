package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.result.XpRewardResult;
import site.omagotchi.learningservice.gamification.domain.AdvancementHistory;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;
import site.omagotchi.learningservice.gamification.domain.LevelPolicy;
import site.omagotchi.learningservice.gamification.domain.LevelState;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.domain.XpSourceType;
import site.omagotchi.learningservice.gamification.domain.XpTransaction;
import site.omagotchi.learningservice.gamification.infrastructure.AdvancementHistoryRepository;
import site.omagotchi.learningservice.gamification.application.port.UserCharacterQueryRepository;
import site.omagotchi.learningservice.gamification.infrastructure.XpTransactionRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class XpRewardService {

    private final UserCharacterQueryRepository userCharacterQueryRepository;
    private final XpTransactionRepository xpTransactionRepository;
    private final AdvancementHistoryRepository advancementHistoryRepository;
    private final CharacterGrowthService characterGrowthService;

    @Transactional
    public XpRewardResult reward(
            UUID userId,
            long amount,
            XpSourceType sourceType,
            Long sourceId
    ) {
        // 같은 원본 이벤트로 보상이 두 번 나가지 않도록 원장 기준으로 먼저 방지
        return xpTransactionRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .map(transaction -> XpRewardResult.from(
                        transaction,
                        characterGrowthService.requireRepresentativeCharacter(userId)
                                .levelState(characterGrowthService.requireLevelPolicies())
                ))
                .orElseGet(() -> createReward(userId, amount, sourceType, sourceId));
    }

    private XpRewardResult createReward(
            UUID userId,
            long amount,
            XpSourceType sourceType,
            Long sourceId
    ) {
        UserCharacter representative = characterGrowthService.requireRepresentativeCharacter(userId);
        // EXP와 레벨은 user_character 한 행에 모이므로 지급 시점에 잠금
        UserCharacter character = userCharacterQueryRepository.getForUpdate(representative.getId());
        List<LevelPolicy> policies = characterGrowthService.requireLevelPolicies();
        XpTransaction existingTransaction = xpTransactionRepository
                .findBySourceTypeAndSourceId(sourceType, sourceId)
                .orElse(null);
        if (existingTransaction != null) {
            return XpRewardResult.from(existingTransaction, character.levelState(policies));
        }

        AdvancementStage previousStage = character.getAdvancementStage();

        XpTransaction transaction = xpTransactionRepository.save(XpTransaction.create(
                userId,
                character.getId(),
                sourceType,
                sourceId,
                amount
        ));

        LevelState levelState = character.addXp(amount, policies);
        // 한 번에 여러 전직 구간을 넘는 보상도 빠진 이력 없이 남김
        saveAdvancementHistories(character, previousStage, levelState, transaction.getId());

        return XpRewardResult.from(transaction, levelState);
    }

    private void saveAdvancementHistories(
            UserCharacter character,
            AdvancementStage previousStage,
            LevelState levelState,
            Long transactionId
    ) {
        for (AdvancementStage stage : AdvancementStage.values()) {
            if (stage == AdvancementStage.BASE || stage.ordinal() <= previousStage.ordinal()
                    || stage.ordinal() > levelState.advancementStage().ordinal()) {
                continue;
            }
            if (!advancementHistoryRepository.existsByUserCharacterIdAndStage(character.getId(), stage)) {
                advancementHistoryRepository.save(AdvancementHistory.create(
                        character.getId(),
                        stage,
                        switch (stage) {
                            case FIRST -> 10;
                            case SECOND -> 20;
                            case THIRD -> 30;
                            case BASE -> 1;
                        },
                        transactionId
                ));
            }
        }
    }
}
