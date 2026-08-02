package site.omagotchi.learningservice.team.application.dto.command;

/**
 * cohortId는 nullable이다 (RM-28).
 * 활성 기수가 1개면 UI에서 선택 단계가 생략되므로 null로 온다.
 *
 * name에 Bean Validation을 걸지 않는 것은 의도다. @Size(max = 30)은 trim 전
 * 원문 길이를 보므로 "30자 + 공백"이 GR-21상 유효한데도 400으로 잘린다.
 * 규칙 판정은 Team.isValidName()이 소유하고 TeamService가 그것을 TEAM_INVALID_NAME으로
 * 옮기므로, 여기서 걸면 같은 규칙이 두 곳에서 서로 다르게 적용된다.
 */
public record CreateTeamRequest (
        Long cohortId,
        String name
)
{
}
