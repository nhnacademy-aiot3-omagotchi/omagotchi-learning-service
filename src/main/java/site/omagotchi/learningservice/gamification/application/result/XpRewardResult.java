package site.omagotchi.learningservice.gamification.application.result;

import site.omagotchi.learningservice.gamification.domain.LevelState;
import site.omagotchi.learningservice.gamification.domain.XpTransaction;

public record XpRewardResult(
        Long transactionId,
        Long userCharacterId,
        long amount,
        LevelState levelState
) {

    public static XpRewardResult from(XpTransaction transaction, LevelState levelState) {
        return new XpRewardResult(
                transaction.getId(),
                transaction.getUserCharacterId(),
                transaction.getAmount(),
                levelState
        );
    }
}
