package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.dto.command.AddTeamMemberRequest;
import site.omagotchi.learningservice.team.application.dto.command.CreateTeamRequest;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamConcurrencyIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamMemberRepository teamMemberRepository;

    @Test
    @DisplayName("정원이 찬 팀에 동시에 추가하면 한 명만 성공한다")
    void test1() throws Exception {
        Long cohortId = fixture.createCohort("1기");
        var master = fixture.createActiveMember(cohortId);
        var team = teamService.create(new CreateTeamRequest(cohortId, "오마고치"), master.userId());

        // 마스터 포함 7명까지 채운다
        for (int i = 0; i < 6; i++) {
            var m = fixture.createActiveMember(cohortId);
            teamMemberService.addMember(team.teamId(),
                    new AddTeamMemberRequest(m.userId()), master.userId());
        }

        var a = fixture.createActiveMember(cohortId);
        var b = fixture.createActiveMember(cohortId);

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (var target : List.of(a, b)) {
            executor.submit(() -> {
                try {
                    start.await();
                    teamMemberService.addMember(team.teamId(),
                            new AddTeamMemberRequest(target.userId()), master.userId());
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    failure.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(1);
        assertThat(teamMemberRepository.countByTeamId(team.teamId())).isEqualTo(8);
    }
}