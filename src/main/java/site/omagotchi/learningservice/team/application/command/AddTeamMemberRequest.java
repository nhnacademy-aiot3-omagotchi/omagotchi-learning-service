package site.omagotchi.learningservice.team.application.command;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 대상은 계정 ID로 지정한다 (GR-03). 수락/거절 절차 없이 즉시 추가된다 (GR-04).
 * 서버가 팀의 cohort_id로 대상의 ACTIVE 멤버십을 역조회하므로 기수 정합(GR-22)이 자동 검증된다.
 */
public record AddTeamMemberRequest(
        @NotNull
        UUID targetUserId
) {
}