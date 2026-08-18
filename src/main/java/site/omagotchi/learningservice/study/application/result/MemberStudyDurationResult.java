package site.omagotchi.learningservice.study.application.result;

/**
 * 다른 Feature에 제공하는 멤버십별 확정 공부시간 합계.
 */
public record MemberStudyDurationResult(
        Long cohortMembershipId,
        long studySeconds
) {
}
