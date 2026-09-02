package site.omagotchi.learningservice.ranking.presentation.response;

import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;

/**
 * 오늘 랭킹 한 줄 응답. 실시간 타이머 반영분을 포함한다.
 *
 * <p>characterType/colorId는 화면이 캐릭터 이미지를 조립하는 데 쓰고,
 * attendanceStreakDays는 날개 단계를 정하는 데 쓴다.
 * 대표 캐릭터가 없는 사용자는 characterType이 null로 내려가며 화면이 기본 캐릭터로 대체한다.
 */
public record TodayStudyRankingEntryResponse(
        long rank,
        String displayName,
        long studySeconds,
        boolean timerRunning,
        String characterType,
        String colorId,
        int attendanceStreakDays
) {

    public static TodayStudyRankingEntryResponse from(StudyRankingEntryResult result) {
        return new TodayStudyRankingEntryResponse(
                result.rank(),
                result.displayName(),
                result.studySeconds(),
                result.timerRunning(),
                result.characterType(),
                result.colorId(),
                result.attendanceStreakDays()
        );
    }
}
