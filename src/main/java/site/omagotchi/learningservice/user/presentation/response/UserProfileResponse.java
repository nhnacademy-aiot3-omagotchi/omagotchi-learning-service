package site.omagotchi.learningservice.user.presentation.response;

import site.omagotchi.learningservice.user.application.result.UserProfileResult;

public record UserProfileResponse(
        String nickname,
        long totalStudySeconds,
        long completedSessionCount,
        int attendanceStreakDays,
        ApprovedCohortResponse approvedCohort,
        CurrentCharacterResponse currentCharacter
) {

    public static UserProfileResponse from(UserProfileResult result) {
        return new UserProfileResponse(
                result.nickname(),
                result.totalStudySeconds(),
                result.completedSessionCount(),
                result.attendanceStreakDays(),
                ApprovedCohortResponse.from(result.approvedCohort()),
                CurrentCharacterResponse.from(result.currentCharacter())
        );
    }
}
