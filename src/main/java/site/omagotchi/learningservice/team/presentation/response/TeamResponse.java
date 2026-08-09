package site.omagotchi.learningservice.team.presentation.response;

import site.omagotchi.learningservice.team.application.result.TeamResult;

import java.time.OffsetDateTime;

/**
 * 팀 요약 응답. 생성 응답과 내 팀 목록(GR-06)에 쓰인다.
 *
 * <p>필드가 {@link TeamResult}와 같아도 별도로 두는 것이 규약이다
 * (10-backend-code-structure §7). Result는 Application의 결과 계약이고 이쪽은 외부
 * 응답 계약이라, 응답에서 필드를 빼거나 이름을 바꿀 때 Application이 함께 흔들리면 안 된다.</p>
 */
public record TeamResponse(
        Long teamId,
        Long cohortId,
        String name,
        OffsetDateTime createdAt
) {

    public static TeamResponse from(TeamResult result) {
        return new TeamResponse(
                result.teamId(),
                result.cohortId(),
                result.name(),
                result.createdAt()
        );
    }
}
