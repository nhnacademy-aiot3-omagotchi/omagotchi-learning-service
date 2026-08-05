package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.gamification.application.result.XpRewardResult;
import site.omagotchi.learningservice.gamification.domain.AdvancementHistory;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;
import site.omagotchi.learningservice.gamification.domain.GamificationErrorCode;
import site.omagotchi.learningservice.gamification.domain.LevelPolicy;
import site.omagotchi.learningservice.gamification.domain.LevelState;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.domain.XpSourceType;
import site.omagotchi.learningservice.gamification.domain.XpTransaction;
import site.omagotchi.learningservice.gamification.infrastructure.AdvancementHistoryRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.gamification.infrastructure.XpTransactionRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class XpRewardService {

    private final UserCharacterRepository userCharacterRepository;
    private final XpTransactionRepository xpTransactionRepository;
    private final AdvancementHistoryRepository advancementHistoryRepository;
    private final CharacterGrowthService characterGrowthService;

    @Transactional
    public XpRewardResult reward(
            UUID userId,
            long amount,
            XpSourceType sourceType,
            String sourceId
    ) {
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
            String sourceId
    ) {
        UserCharacter representative = characterGrowthService.requireRepresentativeCharacter(userId);
        UserCharacter character = userCharacterRepository.findWithLockById(representative.getId())
                .orElseThrow(() -> new BusinessException(GamificationErrorCode.REPRESENTATIVE_CHARACTER_NOT_FOUND));
        AdvancementStage previousStage = character.getAdvancementStage();

        XpTransaction transaction = xpTransactionRepository.save(XpTransaction.create(
                userId,
                character.getId(),
                sourceType,
                sourceId,
                amount
        ));

        List<LevelPolicy> policies = characterGrowthService.requireLevelPolicies();
        LevelState levelState = character.addXp(amount, policies);
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
