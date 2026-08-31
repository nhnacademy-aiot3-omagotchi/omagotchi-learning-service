package site.omagotchi.learningservice.team.application.result;

import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.time.OffsetDateTime;

/**
 * 내부 식별자(user_id, cohort_membership_id)는 담지 않는다 (GR-15).
 * memberId는 team_members.id로 팀 모듈이 소유한 식별자이며,
 * 제외·위임 요청의 대상 지정에 쓰인다.
 *
 * @param displayName Identity Service 조회 결과. 멤버십은 남았지만 일괄 조회 결과에
 *                    계정이 없으면 {@code null}이며, 이때도 팀원 행은 목록에서 제외하지 않는다.
 *                    Identity 연결·서버 오류는 {@code null}로 숨기지 않고 요청을 실패시킨다
 */
public record TeamMemberResult(
        Long memberId,
        String displayName,
        TeamMemberRole role,
        OffsetDateTime joinedAt
) {
}
