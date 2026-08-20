package site.omagotchi.learningservice.team.application;

import java.util.UUID;

/**
 * 팀 모듈이 소유하는 멤버십 표현.
 * cohort 엔티티를 팀 쪽으로 넘기지 않기 위한 경계 DTO다.
 *
 * role과 status를 일부러 담지 않는다 — 팀 생성·가입에는 역할 제한이 없고(v3),
 * status는 MembershipReader가 ACTIVE만 반환하므로 팀 로직에서 쓸 일이 없다.
 * 필드를 열어두면 언젠가 팀 서비스 안에 역할 분기가 생긴다.
 */
public record TeamMembership(
        Long id,
        Long cohortId,
        UUID userId
) {
}