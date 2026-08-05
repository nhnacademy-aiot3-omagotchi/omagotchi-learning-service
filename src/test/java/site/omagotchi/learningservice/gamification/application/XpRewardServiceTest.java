package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;
import site.omagotchi.learningservice.gamification.domain.LevelPolicy;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;
import site.omagotchi.learningservice.gamification.domain.XpSourceType;
import site.omagotchi.learningservice.gamification.domain.XpTransaction;
import site.omagotchi.learningservice.gamification.infrastructure.AdvancementHistoryRepository;
import site.omagotchi.learningservice.gamification.infrastructure.UserCharacterRepository;
import site.omagotchi.learningservice.gamification.infrastructure.XpTransactionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EXP 보상 서비스")
class XpRewardServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Mock
    private XpTransactionRepository xpTransactionRepository;

    @Mock
    private AdvancementHistoryRepository advancementHistoryRepository;

    @Mock
    private CharacterGrowthService characterGrowthService;

    @Test
    @DisplayName("보상 EXP는 수령 시점의 대표 캐릭터에 지급된다")
    void rewardsRepresentativeCharacter() {
        UserCharacter character = UserCharacter.representative(USER_ID, 1L, "야간반장");
        ReflectionTestUtils.setField(character, "id", 7L);
        XpTransaction savedTransaction = XpTransaction.create(
                USER_ID,
                7L,
                XpSourceType.DAILY_QUEST,
                "10",
                100
        );
        ReflectionTestUtils.setField(savedTransaction, "id", 20L);

        XpRewardService service = new XpRewardService(
                userCharacterRepository,
                xpTransactionRepository,
                advancementHistoryRepository,
                characterGrowthService
        );
        when(xpTransactionRepository.findBySourceTypeAndSourceId(XpSourceType.DAILY_QUEST, "10"))
                .thenReturn(Optional.empty());
        when(characterGrowthService.requireRepresentativeCharacter(USER_ID)).thenReturn(character);
        when(userCharacterRepository.findWithLockById(7L)).thenReturn(Optional.of(character));
        when(characterGrowthService.requireLevelPolicies()).thenReturn(List.of(
                LevelPolicy.create(1, 0),
                LevelPolicy.create(2, 100)
        ));
        when(xpTransactionRepository.save(any(XpTransaction.class))).thenReturn(savedTransaction);

        var result = service.reward(USER_ID, 100, XpSourceType.DAILY_QUEST, "10");

        assertAll(
                () -> assertEquals(7L, result.userCharacterId()),
                () -> assertEquals(2, result.levelState().level()),
                () -> assertEquals(100, character.getTotalXp())
        );
    }

    @Test
    @DisplayName("Lv10 경계를 넘으면 전직 이력을 생성한다")
    void createsAdvancementHistory() {
        UserCharacter character = UserCharacter.representative(USER_ID, 1L, "야간반장");
        ReflectionTestUtils.setField(character, "id", 7L);
        XpTransaction savedTransaction = XpTransaction.create(
                USER_ID,
                7L,
                XpSourceType.DAILY_QUEST,
                "10",
                900
        );
        ReflectionTestUtils.setField(savedTransaction, "id", 20L);

        XpRewardService service = new XpRewardService(
                userCharacterRepository,
                xpTransactionRepository,
                advancementHistoryRepository,
                characterGrowthService
        );
        when(xpTransactionRepository.findBySourceTypeAndSourceId(XpSourceType.DAILY_QUEST, "10"))
                .thenReturn(Optional.empty());
        when(characterGrowthService.requireRepresentativeCharacter(USER_ID)).thenReturn(character);
        when(userCharacterRepository.findWithLockById(7L)).thenReturn(Optional.of(character));
        when(characterGrowthService.requireLevelPolicies()).thenReturn(levelPoliciesTo10());
        when(xpTransactionRepository.save(any(XpTransaction.class))).thenReturn(savedTransaction);
        when(advancementHistoryRepository.existsByUserCharacterIdAndStage(7L, AdvancementStage.FIRST))
                .thenReturn(false);

        service.reward(USER_ID, 900, XpSourceType.DAILY_QUEST, "10");

        verify(advancementHistoryRepository).save(any());
        assertEquals(AdvancementStage.FIRST, character.getAdvancementStage());
    }

    private List<LevelPolicy> levelPoliciesTo10() {
        return List.of(
                LevelPolicy.create(1, 0),
                LevelPolicy.create(2, 100),
                LevelPolicy.create(3, 200),
                LevelPolicy.create(4, 300),
                LevelPolicy.create(5, 400),
                LevelPolicy.create(6, 500),
                LevelPolicy.create(7, 600),
                LevelPolicy.create(8, 700),
                LevelPolicy.create(9, 800),
                LevelPolicy.create(10, 900)
        );
    }
}
