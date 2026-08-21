package site.omagotchi.learningservice.team.application.result;

/**
 * 조회 시점의 현재 팀 소속.
 *
 * <p>학습시간의 팀 귀속 정보가 아니라, 다른 Module이 현재 팀원을 필터링하거나
 * 그룹화할 때 사용하는 공개 Application 값이다.</p>
 */
public record CurrentTeamMembershipView(
        Long teamId,
        String teamName,
        Long cohortMembershipId
) {
}
