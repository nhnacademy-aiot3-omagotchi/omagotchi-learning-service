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
import site.omagotchi.learningservice.team.application.result.TeamResult;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.port.TeamRepository;
import site.omagotchi.learningservice.team.infrastructure.TeamJpaRepository;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 팀 이름 유니크의 스코프(GR-21)와 1인 1팀의 스코프(GR-18)를 실제 DB에 대고 고정한다.
 * <p>
 * 이 완료 조건들은 전부 uq_teams_active_name·uq_team_members_membership 이라는
 * "부분" 유니크에 걸려 있다. mock 리포지토리로는 WHERE deleted_at IS NULL 이
 * 실제로 붙어 있는지, LOWER(BTRIM(...)) 기준이 맞는지 아무것도 증명하지 못한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamNameScopeIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamJpaRepository teamJpaRepository;

    @Autowired
    TeamRepository teamRepository;

    @Test
    @DisplayName("다른 기수에는 같은 이름의 팀을 만들 수 있다")
    void canCreateSameNameTeamInDifferentCohort() {
        Long cohort1 = fixture.createCohort("1기");
        Long cohort2 = fixture.createCohort("2기");
        var a = fixture.createActiveMember(cohort1);
        var b = fixture.createActiveMember(cohort2);

        TeamResult first = teamService.create(cohort1, "오마고치", a.userId());
        TeamResult second = teamService.create(cohort2, "오마고치", b.userId());

        assertThat(first.teamId()).isNotEqualTo(second.teamId());
        assertThat(first.name()).isEqualTo(second.name());
    }

    @Test
    @DisplayName("같은 기수에서는 대소문자만 다른 이름도 중복으로 막힌다")
    void blocksDuplicateNameDifferingOnlyInCase() {
        Long cohortId = fixture.createCohort("1기");
        var a = fixture.createActiveMember(cohortId);
        var b = fixture.createActiveMember(cohortId);

        teamService.create(cohortId, "Omagotchi", a.userId());

        String lowerCased = "omagotchi";
        UUID challenger = b.userId();

        assertThatThrownBy(() -> teamService.create(cohortId, lowerCased, challenger))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.DUPLICATE_NAME);
    }

    @Test
    @DisplayName("같은 기수에서는 앞뒤 공백만 다른 이름도 중복으로 막힌다")
    void blocksDuplicateNameDifferingOnlyInWhitespace() {
        Long cohortId = fixture.createCohort("1기");
        var a = fixture.createActiveMember(cohortId);
        var b = fixture.createActiveMember(cohortId);

        teamService.create(cohortId, "오마고치", a.userId());

        String padded = "   오마고치   ";
        UUID challenger = b.userId();

        assertThatThrownBy(() -> teamService.create(cohortId, padded, challenger))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.DUPLICATE_NAME);
    }

    @Test
    @DisplayName("서비스 선검사를 건너뛰어도 uq_teams_active_name이 최종 방어선으로 막는다")
    void uniqueIndexBlocksDuplicateEvenWhenServiceCheckIsBypassed() {
        Long cohortId = fixture.createCohort("1기");
        teamJpaRepository.saveAndFlush(Team.create(cohortId, "오마고치"));

        // Port(TeamRepository)가 아니라 Spring Data 인터페이스를 직접 쓰는 것이 의도다.
        // Port는 이 위반을 DUPLICATE_NAME으로 변환해 감춰버리므로, 인덱스가 실제로 존재하는지
        // 확인하려면 변환 전 예외를 봐야 한다. 변환 자체는 test2·test3이 확인한다.
        //
        // 동시 생성은 두 트랜잭션이 모두 "없음"을 보고 INSERT할 수 있어서,
        // 서비스 선검사가 아니라 이 인덱스가 최종 방어선이다.
        //
        // Team.create를 람다 밖에서 미리 만드는 것은 형식 때문이 아니다.
        // 안에 두면 Team.create가 던진 예외로도 테스트가 통과해, 인덱스를 확인하려던
        // 테스트가 이름 검증을 확인하고 끝날 수 있다.
        Team duplicate = Team.create(cohortId, "오마고치");

        assertThatThrownBy(() -> teamJpaRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Port 계약(flush 시점 + 실패 변환)을 고정한다.
     *
     * <p>서비스를 거치는 test2·test3은 선검사에서 걸리므로 인덱스 변환 경로를 지나지 않는다.
     * 즉 변환이 깨져도 그 테스트들은 초록색이다. 이 테스트가 없으면 flush가 커밋 시점으로
     * 밀리거나 인덱스명이 바뀌었을 때 409가 조용히 500으로 바뀐다.</p>
     */
    @Test
    @DisplayName("Port는 인덱스 위반을 DUPLICATE_NAME으로 변환한다 — flush를 지연하지 않기 때문")
    void portTranslatesIndexViolationToDuplicateName() {
        Long cohortId = fixture.createCohort("1기");
        teamRepository.save(Team.create(cohortId, "오마고치"));

        Team duplicate = Team.create(cohortId, "오마고치");

        assertThatThrownBy(() -> teamRepository.save(duplicate))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.DUPLICATE_NAME);
    }

    @Test
    @DisplayName("해체된 팀과 같은 이름으로 다시 만들 수 있다")
    void canRecreateTeamWithSameNameAfterDisband() {
        Long cohortId = fixture.createCohort("1기");
        var master = fixture.createActiveMember(cohortId);

        TeamResult before = teamService.create(cohortId, "오마고치", master.userId());

        // 마스터가 단독 팀원이므로 탈퇴가 곧 팀 소프트 삭제다 (GR-13).
        teamMemberService.leave(before.teamId(), master.userId());
        assertThat(teamJpaRepository.findById(before.teamId()).orElseThrow().isDisbanded()).isTrue();

        // 이름 유니크와 1인 1팀 유니크가 둘 다 풀려야 통과한다.
        TeamResult after = teamService.create(cohortId, "오마고치", master.userId());

        assertThat(after.teamId()).isNotEqualTo(before.teamId());
    }

    @Test
    @DisplayName("여러 기수를 담당하는 사람은 기수별로 팀에 하나씩 소속될 수 있다")
    void multiCohortPersonCanBelongToOneTeamPerCohort() {
        Long cohort1 = fixture.createCohort("1기");
        Long cohort2 = fixture.createCohort("2기");

        // 한 계정이 두 기수의 멤버십을 동시에 갖는다 — 매니저·멘토의 정상 상태다.
        UUID userId = UUID.randomUUID();
        fixture.createActiveMember(cohort1, userId);
        fixture.createActiveMember(cohort2, userId);

        TeamResult first = teamService.create(cohort1, "1기팀", userId);
        TeamResult second = teamService.create(cohort2, "2기팀", userId);

        // GR-18은 기수 내 제약이므로 두 건 모두 살아 있어야 한다.
        List<TeamResult> myTeams = teamService.getMyTeams(userId);
        assertThat(myTeams).extracting(TeamResult::teamId)
                .containsExactlyInAnyOrder(first.teamId(), second.teamId());
    }

    @Test
    @DisplayName("기수를 지정하지 않으면 담당 기수가 둘 이상인 사람은 거부된다")
    void rejectsWhenCohortIdOmittedAndMultipleActiveCohorts() {
        Long cohort1 = fixture.createCohort("1기");
        Long cohort2 = fixture.createCohort("2기");
        UUID userId = UUID.randomUUID();
        fixture.createActiveMember(cohort1, userId);
        fixture.createActiveMember(cohort2, userId);

        assertThatThrownBy(() ->
                teamService.create(null, "오마고치", userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.COHORT_REQUIRED);
    }
}
