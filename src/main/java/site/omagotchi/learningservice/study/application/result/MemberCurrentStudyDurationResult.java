package site.omagotchi.learningservice.study.application.result;

/**
 * 다른 Feature에 제공하는 멤버십별 현재 집계일 공부시간.
 * 확정 기록과 정상 실행 중 타이머를 합산하며 합계가 0인 멤버십은 반환하지 않는다.
 */
public record MemberCurrentStudyDurationResult(
        Long cohortMembershipId,
        long studySeconds,
        boolean timerRunning
) {
}
