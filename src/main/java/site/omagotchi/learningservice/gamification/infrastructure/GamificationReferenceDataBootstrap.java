package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게이미피케이션 기준 데이터(캐릭터·일일 퀘스트·레벨 구간)를 애플리케이션 기동 시 적재한다.
 *
 * <p>스키마는 Flyway가, 기준 데이터는 이 부트스트랩이 소유한다.
 * 마이그레이션 파일에 INSERT를 섞어 두면 값 하나를 고칠 때마다 새 버전 번호가 필요하고
 * 스키마 압축 시 데이터가 함께 섞여 들어간다.
 *
 * <p>{@link ApplicationRunner}는 Flyway 마이그레이션과 ApplicationContext 준비가 모두 끝난 뒤
 * 실행되므로 테이블과 제약이 존재하는 상태가 보장된다.
 *
 * <p>여러 번 실행해도 안전하도록 모든 문장을 멱등하게 작성했다. 롤링 배포로 인스턴스 두 대가
 * 동시에 기동해도 {@code ON CONFLICT}가 중복 삽입을 흡수한다.
 *
 * <p>실패를 삼키지 않는다. 기준 데이터가 없으면 캐릭터 선택·퀘스트·레벨 계산이 모두 막히므로
 * 반쯤 동작하는 상태로 기동하는 것보다 기동을 실패시키는 편이 낫다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GamificationReferenceDataBootstrap implements ApplicationRunner {

    /** 레벨 N 도달에 필요한 누적 XP = (N - 1)^2 * 100. Lv30 = 84,100 */
    private static final int MAX_LEVEL = 30;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int characters = seedGameCharacters();
        int quests = seedQuestTemplates();
        int levels = seedLevelPolicies();

        log.info(
                "게이미피케이션 기준 데이터 적재 완료 changedCharacters={}, changedQuests={}, changedLevels={}",
                characters,
                quests,
                levels
        );
    }

    /**
     * 캐릭터는 삭제하지 않는다. user_characters.game_character_id가 참조하므로
     * 지우면 사용자가 이미 선택한 캐릭터가 사라진다. 단종은 active = FALSE로 처리한다.
     *
     * <p>active는 갱신 대상에서 제외한다. 운영 중 비활성화한 값을 기동할 때마다 되돌리게 된다.
     *
     * <p>asset_key는 화면의 이미지 폴더명과 1:1로 묶여 있고 UNIQUE 제약이 걸려 있다.
     * 두 캐릭터의 asset_key를 서로 맞바꾸는 변경은 이 문장 하나로 처리할 수 없다.
     */
    private int seedGameCharacters() {
        return jdbcTemplate.update("""
                INSERT INTO learning_service.game_characters (name, code, description, asset_key)
                VALUES
                    ('야간반',     'NIGHT_CLASS', '늦은 시간에도 집중력을 붙잡는 야간형 오마고치입니다.', 'night'),
                    ('공부쟁이',   'STUDY',       '기본기가 탄탄한 학습형 오마고치입니다.',              'study'),
                    ('디버깅이',   'DEBUG',       '문제를 발견하면 끝까지 추적하는 오마고치입니다.',      'debug'),
                    ('새싹이',     'SPROUT',      '작은 출석에도 크게 반응하는 성장형 오마고치입니다.',   'sprout'),
                    ('서버지킴이', 'SERVER',      '꾸준한 루틴과 긴 집중에 어울리는 오마고치입니다.',     'server'),
                    ('잼민이',     'KID',         '공부에 리듬을 만들어 주는 에너지형 오마고치입니다.',   'kid'),
                    ('카페인이',   'CAFFEINE',    '짧고 강한 집중에 특화된 오마고치입니다.',            'caffeine'),
                    ('커밋이',     'COMMIT',      '기록과 회고를 좋아하는 습관형 오마고치입니다.',        'commit')
                ON CONFLICT (code) DO UPDATE SET
                    name        = EXCLUDED.name,
                    description = EXCLUDED.description,
                    asset_key   = EXCLUDED.asset_key,
                    updated_at  = CURRENT_TIMESTAMP
                WHERE game_characters.name        IS DISTINCT FROM EXCLUDED.name
                   OR game_characters.description IS DISTINCT FROM EXCLUDED.description
                   OR game_characters.asset_key   IS DISTINCT FROM EXCLUDED.asset_key
                """);
    }

    /**
     * 하루 최대 획득 XP = 20 + 30 + 10 + 20 + 40 = 120.
     *
     * <p>퀘스트 템플릿도 삭제하지 않는다. user_daily_quests.template_id가 참조한다.
     * 운영을 중단할 퀘스트는 active = FALSE로 처리한다.
     *
     * <p>이미 발급된 user_daily_quests는 발급 시점의 제목·목표·보상을 복사해 갖는다.
     * 여기서 보상을 바꿔도 오늘 이미 받은 퀘스트에는 소급되지 않고 다음 발급분부터 적용된다.
     */
    private int seedQuestTemplates() {
        return jdbcTemplate.update("""
                INSERT INTO learning_service.quest_templates
                    (type, code, title, target_count, reward_xp, display_order)
                VALUES
                    ('ROUTINE', 'ATTENDANCE',        '출석하기',          1, 20, 1),
                    ('ROUTINE', 'STUDY_COMPLETED',   '학습 완료하기',      1, 30, 2),
                    ('ROUTINE', 'CHARACTER_CHECKED', '캐릭터 확인하기',    1, 10, 3),
                    ('ROUTINE', 'ROUTINE_REVIEW',    '오늘 학습 돌아보기',  1, 20, 4),
                    ('LLM',     'LLM_QUEST',         'AI 추천 퀘스트',     1, 40, 5)
                ON CONFLICT (code) DO UPDATE SET
                    type          = EXCLUDED.type,
                    title         = EXCLUDED.title,
                    target_count  = EXCLUDED.target_count,
                    reward_xp     = EXCLUDED.reward_xp,
                    display_order = EXCLUDED.display_order
                WHERE quest_templates.type          IS DISTINCT FROM EXCLUDED.type
                   OR quest_templates.title         IS DISTINCT FROM EXCLUDED.title
                   OR quest_templates.target_count  IS DISTINCT FROM EXCLUDED.target_count
                   OR quest_templates.reward_xp     IS DISTINCT FROM EXCLUDED.reward_xp
                   OR quest_templates.display_order IS DISTINCT FROM EXCLUDED.display_order
                """);
    }

    /**
     * 레벨 구간은 min_total_xp에 UNIQUE 인덱스가 걸려 있어 행 단위 UPSERT로 곡선을 바꾸면
     * 중간 상태에서 값이 겹쳐 실패할 수 있다. 그래서 전량 교체한다.
     * 이 테이블을 참조하는 외래 키가 없고 한 트랜잭션에서 실행되므로 안전하다.
     *
     * <p>다만 매 기동마다 30행을 지웠다 넣지 않도록, 값이 이미 일치하면 아무것도 하지 않는다.
     */
    private int seedLevelPolicies() {
        if (levelPoliciesUpToDate()) {
            return 0;
        }

        jdbcTemplate.update("DELETE FROM learning_service.level_policies");
        return jdbcTemplate.update("""
                INSERT INTO learning_service.level_policies (level, min_total_xp)
                SELECT level, (level - 1) * (level - 1) * 100
                FROM generate_series(1, ?) AS level
                """, MAX_LEVEL);
    }

    private boolean levelPoliciesUpToDate() {
        Integer mismatched = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM generate_series(1, ?) AS level
                FULL OUTER JOIN learning_service.level_policies policy
                    ON policy.level = level
                WHERE policy.level IS NULL
                   OR level IS NULL
                   OR policy.min_total_xp <> (level - 1) * (level - 1) * 100
                """, Integer.class, MAX_LEVEL);
        return mismatched != null && mismatched == 0;
    }
}
