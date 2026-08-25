package site.omagotchi.learningservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.attendance.application.AttendanceService;
import site.omagotchi.learningservice.cohort.application.CohortAttendancePolicyService;
import site.omagotchi.learningservice.cohort.application.CohortManagerService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.JoinCodeService;
import site.omagotchi.learningservice.cohort.application.command.ApproveMembershipCommand;
import site.omagotchi.learningservice.cohort.application.command.AssignCohortManagerCommand;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateCohortCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateJoinCommand;
import site.omagotchi.learningservice.cohort.application.command.IssueJoinCodeCommand;
import site.omagotchi.learningservice.cohort.application.command.SaveAttendancePolicyCommand;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.gamification.application.DailyQuestService;
import site.omagotchi.learningservice.gamification.application.CharacterOnboardingService;
import site.omagotchi.learningservice.gamification.application.command.CreateUserCharacterCommand;
import site.omagotchi.learningservice.gamification.application.result.DailyQuestResult;
import site.omagotchi.learningservice.gamification.domain.QuestStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("SYSTEM_ADMIN 기수 생성부터 사용자 출석 퀘스트까지")
class SystemAdminCohortAttendanceQuestFlowIT {

    private static final UUID SYSTEM_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");

    @Autowired CohortService cohortService;
    @Autowired CohortManagerService cohortManagerService;
    @Autowired CohortAttendancePolicyService attendancePolicyService;
    @Autowired JoinCodeService joinCodeService;
    @Autowired CohortMembershipService membershipService;
    @Autowired AttendanceService attendanceService;
    @Autowired DailyQuestService dailyQuestService;
    @Autowired CharacterOnboardingService characterOnboardingService;

    @Test
    @DisplayName("기수 생성·가입 승인 후 체크인이 기록되고 출석 퀘스트가 완료된다")
    void createsCohortChecksInAndCompletesAttendanceQuest() throws Exception {
        LocalDate today = LocalDate.now();
        var cohort = cohortService.create(
                new CreateCohortCommand("검증 기수", "SYSTEM_ADMIN 전체 흐름 검증", today.minusDays(1), today.plusMonths(1)),
                SYSTEM_ADMIN_ID,
                GlobalRole.SYSTEM_ADMIN
        );
        assertEquals(CohortStatus.PREPARING, cohort.status());

        cohortManagerService.assignManager(
                cohort.id(),
                new AssignCohortManagerCommand(MANAGER_ID),
                SYSTEM_ADMIN_ID,
                GlobalRole.SYSTEM_ADMIN
        );
        attendancePolicyService.savePolicy(
                cohort.id(),
                new SaveAttendancePolicyCommand("Asia/Seoul", LocalTime.of(9, 0), LocalTime.of(18, 0),
                        LocalTime.of(10, 0), 30),
                MANAGER_ID
        );
        var activeCohort = cohortService.changeStatus(
                cohort.id(),
                new ChangeCohortStatusCommand(CohortStatus.ACTIVE),
                GlobalRole.SYSTEM_ADMIN
        );
        assertEquals(CohortStatus.ACTIVE, activeCohort.status());

        var joinCode = joinCodeService.issue(
                cohort.id(),
                new IssueJoinCodeCommand(OffsetDateTime.now().plusDays(1)),
                MANAGER_ID
        );
        var application = membershipService.join(new CreateJoinCommand(joinCode.code()), STUDENT_ID);
        assertEquals(CohortMembershipStatus.PENDING, application.status());

        var membership = membershipService.approve(
                application.id(),
                new ApproveMembershipCommand(CohortMembershipRole.STUDENT),
                MANAGER_ID,
                GlobalRole.USER
        );
        assertEquals(CohortMembershipStatus.ACTIVE, membership.status());

        characterOnboardingService.createRepresentativeCharacter(
                STUDENT_ID,
                new CreateUserCharacterCommand(1L, "검증오마", "pistachio")
        );
        dailyQuestService.getOrCreateDailyQuests(STUDENT_ID);
        var attendance = attendanceService.checkIn(cohort.id(), STUDENT_ID);
        assertNotNull(attendance.id());
        assertNotNull(attendance.checkedInAt());

        DailyQuestResult attendanceQuest = waitForCompletedAttendanceQuest();
        assertEquals(DailyQuestService.ATTENDANCE_CODE, attendanceQuest.code());
        assertEquals(1, attendanceQuest.progressCount());
        assertEquals(QuestStatus.COMPLETED, attendanceQuest.status());

        var myAttendance = attendanceService.getMyRecords(cohort.id(), STUDENT_ID,
                site.omagotchi.learningservice.attendance.application.query.AttendancePageQuery.of(
                        today, today, 0, 20));
        assertEquals(1, myAttendance.items().size());
        assertEquals(attendance.id(), myAttendance.items().getFirst().id());
        assertTrue(dailyQuestService.getHome(STUDENT_ID).dailyQuests().stream()
                .anyMatch(quest -> DailyQuestService.ATTENDANCE_CODE.equals(quest.code())
                        && quest.status() == QuestStatus.COMPLETED));
    }

    private DailyQuestResult waitForCompletedAttendanceQuest() throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            var quest = dailyQuestService.getOrCreateDailyQuests(STUDENT_ID).stream()
                    .filter(candidate -> DailyQuestService.ATTENDANCE_CODE.equals(candidate.code()))
                    .findFirst()
                    .orElseThrow();
            if (quest.status() == QuestStatus.COMPLETED) {
                return quest;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("출석 체크인 이벤트가 출석 퀘스트를 완료하지 못했습니다.");
    }
}
