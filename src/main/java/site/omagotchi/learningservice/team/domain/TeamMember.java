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

    /**
     * 팀 정원 (GR-17). 마스터를 포함한 인원이다.
     *
     * <p>"최대 N행"은 유니크 인덱스로 표현할 수 없어 DB가 막아주지 못한다.
     * 이 상수를 쓰는 카운트는 반드시 {@code teams} 행 락 안에서 수행해야 하며,
     * 락 밖에서 세면 7명 팀에 둘이 동시에 들어와 9명이 된다.</p>
     */
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

    /**
     * 팀 생성자를 MASTER로 등록한다 (GR-02).
     *
     * <p>팀당 MASTER는 {@code uq_team_members_one_master}로 최대 1명이 보장되므로,
     * 이미 MASTER가 있는 팀에 이 팩토리로 행을 넣으면 유니크 위반이 난다.
     * 기존 MASTER를 {@link #demote()}로 내린 뒤에만 새 MASTER를 만들 수 있다.</p>
     *
     * @param cohortMembershipId 주체 키. user_id가 아니라 멤버십 id다
     */
    public static TeamMember master(Long teamId, Long cohortMembershipId) {
        return of(teamId, cohortMembershipId, TeamMemberRole.MASTER);
    }

    /**
     * 일반 팀원으로 등록한다 (GR-03).
     *
     * <p>이 팩토리는 정합성을 검사하지 않는다. 대상 멤버십의 기수가 팀의 기수와
     * 같은지(GR-22), 정원이 남았는지(GR-17)는 호출 전에 서비스가 확인해야 한다.
     * 특히 GR-22는 {@code team_members}에 cohort_id가 없어 DB가 막지 못한다.</p>
     *
     * @param cohortMembershipId 주체 키. user_id가 아니라 멤버십 id다
     */
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

    /** 팀 관리 권한(팀원 추가·제외·위임·해체)의 유일한 판정 기준이다. */
    public boolean isMaster() {
        return role == TeamMemberRole.MASTER;
    }

    /**
     * MASTER → MEMBER 강등.
     *
     * <p>위임 시 반드시 demote를 먼저 flush한 뒤 {@link #promote()}해야 한다.
     * 역순이면 순간적으로 MASTER가 2명이 되어 {@code uq_team_members_one_master}를 위반한다.</p>
     */
    public void demote() {
        this.role = TeamMemberRole.MEMBER;
    }

    /**
     * MEMBER → MASTER 승격.
     *
     * <p>{@link #demote()}로 기존 MASTER를 내리고 flush한 뒤에만 호출해야 한다.
     * 순서를 지켜도 팀에 MASTER가 0명이 되는 순간이 생기므로, 이 두 호출은
     * 반드시 같은 트랜잭션 안에 있어야 한다 — DB는 "최대 1명"만 보장하고
     * "최소 1명"은 전적으로 트랜잭션 책임이다.</p>
     */
    public void promote() {
        this.role = TeamMemberRole.MASTER;
    }
}