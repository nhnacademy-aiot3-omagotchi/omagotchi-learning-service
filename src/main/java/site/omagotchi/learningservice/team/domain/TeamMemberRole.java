package site.omagotchi.learningservice.team.domain;

/**
 * 팀 내 역할. 팀당 MASTER는 정확히 1명이라는 것이 이 도메인의 핵심 불변식이다.
 *
 * <p>"최대 1명"은 {@code uq_team_members_one_master} 부분 유니크가 보장하지만,
 * "최소 1명"은 DB로 표현할 수 없어 위임·탈퇴·해체 트랜잭션이 직접 책임진다.
 * MASTER가 0명인 팀이 남으면 아무도 그 팀을 해체하거나 팀원을 관리할 수 없다.</p>
 *
 * <p>기수 멤버십의 역할(매니저·멘토·학생)과는 무관하다. 팀 생성·가입에 역할 제한이
 * 없으므로(명세 05 v3) 매니저도 담당 기수의 팀에서 일반 MEMBER일 수 있다.</p>
 *
 * <p>DB의 {@code ck_team_members_role} CHECK와 상수명이 일치해야 한다 —
 * {@code @Enumerated(EnumType.STRING)}으로 이름 그대로 저장되기 때문이다.</p>
 */
public enum TeamMemberRole {

    /** 일반 팀원. 팀 관리 권한이 없고 탈퇴만 스스로 할 수 있다. */
    MEMBER,

    /** 팀 마스터. 팀원 추가·제외·위임·해체를 수행한다. 팀당 1명. */
    MASTER
}
