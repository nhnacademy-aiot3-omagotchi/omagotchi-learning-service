package site.omagotchi.learningservice.team.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 팀 소속. 상태 컬럼이 없고 행의 존재 자체가 소속을 뜻한다.
 * 탈퇴·제외·해체는 모두 물리 삭제다 — 소프트 삭제하면 옛 행이
 * uq_team_members_membership을 계속 점유해 재가입이 영구히 불가능해진다.
 *
 * 주체 키는 user_id가 아니라 cohort_membership_id다. 멤버십은 기수당 1행이므로
 * 이 컬럼의 단독 유니크가 곧 "기수당 1인 1팀"(GR-18)이 된다.
 * 여러 기수를 담당하는 매니저·멘토가 각 기수의 팀에 하나씩 소속되는 것은 정상이다.
 */
@Entity
@Table(name = "team_members", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

    public static final int MAX_MEMBERS_PER_TEAM = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "cohort_membership_id", nullable = false)
    private Long cohortMembershipId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamMemberRole role;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    public static TeamMember master(Long teamId, Long cohortMembershipId) {
        return of(teamId, cohortMembershipId, TeamMemberRole.MASTER);
    }

    public static TeamMember member(Long teamId, Long cohortMembershipId) {
        return of(teamId, cohortMembershipId, TeamMemberRole.MEMBER);
    }

    private static TeamMember of(Long teamId, Long cohortMembershipId, TeamMemberRole role) {
        TeamMember teamMember = new TeamMember();
        teamMember.teamId = teamId;
        teamMember.cohortMembershipId = cohortMembershipId;
        teamMember.role = role;
        teamMember.joinedAt = OffsetDateTime.now();
        return teamMember;
    }

    public boolean isMaster() {
        return role == TeamMemberRole.MASTER;
    }

    /**
     * 위임 시 반드시 demote를 먼저 flush한 뒤 promote해야 한다.
     * 역순이면 순간적으로 MASTER가 2명이 되어 uq_team_members_one_master를 위반한다.
     */
    public void demote() {
        this.role = TeamMemberRole.MEMBER;
    }

    public void promote() {
        this.role = TeamMemberRole.MASTER;
    }
}