package site.omagotchi.learningservice.user.application.result;

public record UserProfileResult(
        String nickname,
        long totalStudySeconds,
        long completedSessionCount,
        int attendanceStreakDays,
        ApprovedCohortResult approvedCohort,
        CurrentCharacterResult currentCharacter
) {
}
