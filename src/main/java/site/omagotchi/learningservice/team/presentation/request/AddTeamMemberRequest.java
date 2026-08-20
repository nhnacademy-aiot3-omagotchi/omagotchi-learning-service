package site.omagotchi.learningservice.team.presentation.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 팀원 추가 요청.
 *
 * <p>대상은 계정 ID로 지정한다 (GR-03). 수락·거절 절차 없이 즉시 추가된다 (GR-04).</p>
 *
 * <p>기수나 멤버십 식별자를 받지 않는 것이 의도다. 서버가 팀의 {@code cohort_id}로
 * 대상의 ACTIVE 멤버십을 역조회하므로 기수 정합(GR-22)이 조회 결과로 자동 검증된다 —
 * 요청으로 받으면 팀의 기수와 다른 기수로 추가하는 경로가 열린다.</p>
 */
public record AddTeamMemberRequest(
        @NotNull(message = "대상 사용자 ID는 필수입니다.")
        UUID targetUserId
) {
}
