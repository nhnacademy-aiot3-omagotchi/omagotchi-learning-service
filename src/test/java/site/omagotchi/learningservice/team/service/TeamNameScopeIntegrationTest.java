package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.dto.command.CreateTeamRequest;
import site.omagotchi.learningservice.team.application.dto.result.TeamResponse;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamErrorCode;
import site.omagotchi.learningservice.team.infrastructure.TeamRepository;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 팀 이름 유니크의 스코프(GR-21)와 1인 1팀의 스코프(GR-18)를 실제 DB에 대고 고정한다.
 *
 * 이 완료 조건들은 전부 uq_teams_active_name·uq_team_members_membership 이라는
 * "부분" 유니크에 걸려 있다. mock 리포지토리로는 WHERE deleted_at IS NULL 이
 * 실제로 붙어 있는지, LOWER(BTRIM(...)) 기준이 맞는지 아무것도 증명하지 못한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamNameScopeIntegrationTest {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamRepository teamRepository;

    @Test
    @DisplayName("다른 기수에는 같은 이름의 팀을 만들 수 있다")
    void test1() {
        Long cohort1 = fixture.createCohort("1기");
        Long cohort2 = fixture.createCohort("2기");
        var a = fixture.createActiveMember(cohort1);
        var b = fixture.createActiveMember(cohort2);

        TeamResponse first = teamService.create(new CreateTeamRequest(cohort1, "오마고치"), a.userId());
        TeamResponse second = teamService.create(new CreateTeamRequest(cohort2, "오마고치"), b.userId());

        assertThat(first.teamId()).isNotEqualTo(second.teamId());
        assertThat(first.name()).isEqualTo(second.name());
    }

    @Test
    @DisplayName("같은 기수에서는 대소문자만 다른 이름도 중복으로 막힌다")
    void test2() {
        Long cohortId = fixture.createCohort("1기");
        var a = fixture.createActiveMember(cohortId);
        var b = fixture.createActiveMember(cohortId);

        teamService.create(new CreateTeamRequest(cohortId, "Omagotchi"), a.userId());

        assertThatThrownBy(() ->
                teamService.create(new CreateTeamRequest(cohortId, "omagotchi"), b.userId()))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.DUPLICATE_NAME);
    }

    @Test
    @DisplayName("같은 기수에서는 앞뒤 공백만 다른 이름도 중복으로 막힌다")
    void test3() {
        Long cohortId = fixture.createCohort("1기");
        var a = fixture.createActiveMember(cohortId);
        var b = fixture.createActiveMember(cohortId);

        teamService.create(new CreateTeamRequest(cohortId, "오마고치"), a.userId());

        assertThatThrownBy(() ->
                teamService.create(new CreateTeamRequest(cohortId, "   오마고치   "), b.userId()))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.DUPLICATE_NAME);
    }

    @Test
    @DisplayName("서비스 선검사를 건너뛰어도 uq_teams_active_name이 최종 방어선으로 막는다")
    void test4() {
        Long cohortId = fixture.createCohort("1기");
        teamRepository.saveAndFlush(Team.create(cohortId, "오마고치"));

        // 동시 생성은 두 트랜잭션이 모두 "없음"을 보고 INSERT할 수 있다.
        // 선검사가 아니라 인덱스가 실제로 막아야 한다.
        assertThatThrownBy(() -> teamRepository.saveAndFlush(Team.create(cohortId, "오마고치")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("해체된 팀과 같은 이름으로 다시 만들 수 있다")
    void test5() {
        Long cohortId = fixture.createCohort("1기");
        var master = fixture.createActiveMember(cohortId);

        TeamResponse before = teamService.create(new CreateTeamRequest(cohortId, "오마고치"), master.userId());

        // 마스터가 단독 팀원이므로 탈퇴가 곧 팀 소프트 삭제다 (GR-13).
        teamMemberService.leave(before.teamId(), master.userId());
        assertThat(teamRepository.findById(before.teamId()).orElseThrow().isDisbanded()).isTrue();

        // 이름 유니크와 1인 1팀 유니크가 둘 다 풀려야 통과한다.
        TeamResponse after = teamService.create(new CreateTeamRequest(cohortId, "오마고치"), master.userId());

        assertThat(after.teamId()).isNotEqualTo(before.teamId());
    }

    @Test
    @DisplayName("여러 기수를 담당하는 사람은 기수별로 팀에 하나씩 소속될 수 있다")
    void test6() {
        Long cohort1 = fixture.createCohort("1기");
        Long cohort2 = fixture.createCohort("2기");

        // 한 계정이 두 기수의 멤버십을 동시에 갖는다 — 매니저·멘토의 정상 상태다.
        UUID userId = UUID.randomUUID();
        fixture.createActiveMember(cohort1, userId);
        fixture.createActiveMember(cohort2, userId);

        TeamResponse first = teamService.create(new CreateTeamRequest(cohort1, "1기팀"), userId);
        TeamResponse second = teamService.create(new CreateTeamRequest(cohort2, "2기팀"), userId);

        // GR-18은 기수 내 제약이므로 두 건 모두 살아 있어야 한다.
        List<TeamResponse> myTeams = teamService.getMyTeams(userId);
        assertThat(myTeams).extracting(TeamResponse::teamId)
                .containsExactlyInAnyOrder(first.teamId(), second.teamId());
    }

    @Test
    @DisplayName("기수를 지정하지 않으면 담당 기수가 둘 이상인 사람은 거부된다")
    void test7() {
        Long cohort1 = fixture.createCohort("1기");
        Long cohort2 = fixture.createCohort("2기");
        UUID userId = UUID.randomUUID();
        fixture.createActiveMember(cohort1, userId);
        fixture.createActiveMember(cohort2, userId);

        assertThatThrownBy(() ->
                teamService.create(new CreateTeamRequest(null, "오마고치"), userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.COHORT_REQUIRED);
    }
}
