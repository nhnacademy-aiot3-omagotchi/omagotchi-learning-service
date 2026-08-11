package site.omagotchi.learningservice.team.application.result;

import site.omagotchi.learningservice.team.domain.Team;

import java.time.OffsetDateTime;

/**
 * 팀 요약. 생성 결과와 내 팀 목록(GR-06)에 쓰인다.
 *
 * <p>팀원 정보가 없는 것이 상세({@link TeamDetailResult})와의 차이다.
 * 목록에서 팀마다 팀원을 채우면 표시명 조회가 팀 수만큼 늘어나므로,
 * 팀원은 상세를 열 때만 가져온다.</p>
 *
 * <p>{@code cohortId}를 담는다. 다기수 담당자가 목록에서 어느 기수의 팀인지
 * 구분해야 하기 때문이며, 감추라고 한 내부 식별자(user_id, cohort_membership_id)와는
 * 성격이 다르다 (GR-15).</p>
 */
public record TeamResult(
        Long teamId,
        Long cohortId,
        String name,
        OffsetDateTime createdAt
) {
    /** 저장 직후 호출할 때는 team이 flush되어 id가 채워진 상태여야 한다. */
    public static TeamResult from(Team team) {
        return new TeamResult(
                team.getId(),
                team.getCohortId(),
                team.getName(),
                team.getCreatedAt()
        );
    }
}
