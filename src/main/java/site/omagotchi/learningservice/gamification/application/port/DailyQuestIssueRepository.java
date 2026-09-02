package site.omagotchi.learningservice.gamification.application.port;

import site.omagotchi.learningservice.gamification.domain.UserDailyQuest;

import java.util.List;

/**
 * 일일 퀘스트 발급 저장 계약.
 *
 * <p>발급은 "오늘 것이 있는지 읽고 없으면 쓴다"라서 두 요청이 같은 순간에 들어오면
 * 둘 다 "없음"으로 읽고 둘 다 INSERT를 시도한다. 늦은 쪽은
 * {@code uq_user_daily_quests_user_date_code} 위반으로 실패한다.
 * 그래서 저장은 중복을 흡수하는 방식이어야 한다.
 */
public interface DailyQuestIssueRepository {

    /**
     * 이미 있는 {@code (user_id, quest_date, code)}는 건너뛰고 없는 것만 넣는다.
     *
     * @return 실제로 삽입된 건수
     */
    int issueIfAbsent(List<UserDailyQuest> quests);
}
