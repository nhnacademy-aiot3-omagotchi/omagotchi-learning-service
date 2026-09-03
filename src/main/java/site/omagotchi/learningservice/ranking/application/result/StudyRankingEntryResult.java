package site.omagotchi.learningservice.ranking.application.result;

/**
 * 랭킹 한 줄. 화면이 캐릭터와 날개까지 그릴 수 있도록 외형 정보를 함께 싣는다.
 *
 * <p>characterType은 Frontend 이미지 폴더명, colorId는 그 안의 파일을 고른다.
 * 대표 캐릭터가 없는 사용자도 순위에는 남아야 하므로 둘 다 null을 허용하고,
 * 화면이 기본 캐릭터로 대체한다.
 *
 * <p>attendanceStreakDays는 평일 연속 출석일이다. 날개 단계 환산은 화면이 맡는다.
 * 홈 캐릭터와 같은 규칙을 두 곳에 복제하지 않기 위해서다.
 */
public record StudyRankingEntryResult(
        long rank,
        String displayName,
        long studySeconds,
        boolean timerRunning,
        String characterType,
        String colorId,
        int attendanceStreakDays
) {

    public StudyRankingEntryResult(long rank, String displayName, long studySeconds) {
        this(rank, displayName, studySeconds, false, null, null, 0);
    }

    public StudyRankingEntryResult(long rank, String displayName, long studySeconds, boolean timerRunning) {
        this(rank, displayName, studySeconds, timerRunning, null, null, 0);
    }
}
