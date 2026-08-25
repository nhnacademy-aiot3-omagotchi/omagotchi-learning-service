package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.port.TeamNotificationSender;
import site.omagotchi.learningservice.team.application.port.TeamNotificationSender.DisbandNotice;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 팀 해체 통보 (GR-19, 명세 06 §2 4단계).
 *
 * <p><b>배선을 확인하는 것이 이 IT의 목적이다.</b> 통보는 {@code AFTER_COMMIT} + {@code @Async}라
 * 해체 Transaction 밖에서 돌고, 수신자는 <b>물리 삭제되기 전에</b> 잡아 이벤트에 실어야 한다 —
 * 어느 한쪽이 어긋나면 아무에게도 가지 않거나 빈 목록이 간다. 발송 내용의 규칙은
 * {@code TeamDisbandNotifierTest}가 본다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamDisbandNoticeIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamMasterService teamMasterService;

    @MockitoBean
    TeamNotificationSender sender;

    /**
     * 수신자를 <b>삭제 전에</b> 잡는다는 계약을 고정한다. {@code deleteByTeamId}가 물리
     * 삭제라, 커밋 후 리스너가 도는 시점에는 팀원을 조회할 방법이 없다 — 순서가 뒤집히면
     * 발송 대상이 0건이 된다.
     */
    @Test
    @DisplayName("팀을 해체하면 (구)팀원에게 통보가 발송된다.")
    void notifiesFormerMembersOnDisband() {
        Long cohortId = fixture.createCohort("해체통보-발송");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member memberA = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member memberB = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "해체통보-발송팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, memberA.userId(), master.userId());
        teamMemberService.addMember(teamId, memberB.userId(), master.userId());

        teamMasterService.disband(teamId, master.userId());

        ArgumentCaptor<DisbandNotice> captor = ArgumentCaptor.forClass(DisbandNotice.class);
        verify(sender, timeout(5_000).times(2)).sendDisbandNotice(captor.capture());

        // 해체한 마스터는 제외된다 — 자기가 누른 버튼의 결과를 다시 알릴 이유가 없다.
        assertThat(captor.getAllValues())
                .extracting(DisbandNotice::recipientUserId)
                .containsExactlyInAnyOrder(memberA.userId(), memberB.userId())
                .doesNotContain(master.userId());

        // 팀 이름은 해체 시점 스냅샷이다 — 여러 기수에 속할 수 있어 이름이 없으면
        // 어느 팀이 사라졌는지 알 수 없다.
        assertThat(captor.getAllValues())
                .allSatisfy(notice -> {
                    assertThat(notice.teamId()).isEqualTo(teamId);
                    assertThat(notice.teamName()).isEqualTo("해체통보-발송팀");
                    assertThat(notice.disbandedAt()).isNotNull();
                });
    }

    /**
     * 마스터 혼자였던 팀은 받을 사람이 없다 — GR-16의 자동 해체가 통보하지 않는 것과 같은
     * 상황이며, 이벤트 자체를 발행하지 않는다.
     */
    @Test
    @DisplayName("마스터 혼자인 팀을 해체하면 통보하지 않는다.")
    void doesNotNotifyWhenMasterWasAlone() {
        Long cohortId = fixture.createCohort("해체통보-혼자");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "해체통보-혼자팀", master.userId()).teamId();

        teamMasterService.disband(teamId, master.userId());

        verify(sender, never()).sendDisbandNotice(any());
    }
}
