-- 테스트 전용 기준 데이터 시드
--
-- 운영에서는 GamificationReferenceDataBootstrap(ApplicationRunner)이 기동 시 적재한다.
-- 그런데 @DataJpaTest 같은 슬라이스 테스트는 @Component 를 스캔하지 않아
-- ApplicationRunner 가 실행되지 않는다. 그래서 테스트에서는 Flyway 로 같은 데이터를 채운다.
--
-- application-test.yaml 의 spring.flyway.locations 에 classpath:db/seed 가 추가되어 있어야
-- 이 파일이 적용된다. 운영 classpath(src/main/resources)에는 존재하지 않는다.
--
-- 값은 GamificationReferenceDataBootstrap 과 일치해야 한다.
-- 한쪽만 바꾸면 테스트와 운영이 서로 다른 데이터를 보게 된다.

INSERT INTO learning_service.game_characters (name, code, description, asset_key) VALUES
    ('야간반',     'NIGHT_CLASS', '늦은 시간에도 집중력을 붙잡는 야간형 오마고치입니다.', 'night'),
    ('공부쟁이',   'STUDY',       '기본기가 탄탄한 학습형 오마고치입니다.',              'study'),
    ('디버깅이',   'DEBUG',       '문제를 발견하면 끝까지 추적하는 오마고치입니다.',      'debug'),
    ('새싹이',     'SPROUT',      '작은 출석에도 크게 반응하는 성장형 오마고치입니다.',   'sprout'),
    ('서버지킴이', 'SERVER',      '꾸준한 루틴과 긴 집중에 어울리는 오마고치입니다.',     'server'),
    ('잼민이',     'KID',         '공부에 리듬을 만들어 주는 에너지형 오마고치입니다.',   'kid'),
    ('카페인이',   'CAFFEINE',    '짧고 강한 집중에 특화된 오마고치입니다.',            'caffeine'),
    ('커밋이',     'COMMIT',      '기록과 회고를 좋아하는 습관형 오마고치입니다.',        'commit')
ON CONFLICT (code) DO NOTHING;

INSERT INTO learning_service.quest_templates
    (type, code, title, target_count, reward_xp, display_order) VALUES
    ('ROUTINE', 'ATTENDANCE',        '출석하기',          1, 20, 1),
    ('ROUTINE', 'STUDY_COMPLETED',   '학습 완료하기',      1, 30, 2),
    ('ROUTINE', 'CHARACTER_CHECKED', '캐릭터 확인하기',    1, 10, 3),
    ('ROUTINE', 'ROUTINE_REVIEW',    '오늘 학습 돌아보기',  1, 20, 4),
    ('LLM',     'LLM_QUEST',         'AI 추천 퀘스트',     1, 40, 5)
ON CONFLICT (code) DO NOTHING;

INSERT INTO learning_service.level_policies (level, min_total_xp)
SELECT level, (level - 1) * (level - 1) * 100
FROM generate_series(1, 30) AS level
ON CONFLICT (level) DO NOTHING;
