package site.omagotchi.learningservice.team.application.dto.command;

/**
 * cohortId는 nullable이다 (RM-28).
 * 활성 기수가 1개면 UI에서 선택 단계가 생략되므로 null로 온다.
 *
 * name에 Bean Validation을 걸지 않는 것은 의도다. @Size(max = 30)은 trim 전
 * 원문 길이를 보므로 "30자 + 공백"이 GR-21상 유효한데도 400으로 잘린다.
 * 길이·공백 규칙은 Team.normalizeName()이 단독으로 소유하며,
 * 그래야 실패 응답도 TEAM_INVALID_NAME으로 일관된다.
 */
public record CreateTeamRequest (
        Long cohortId,
        String name
)
{
}
