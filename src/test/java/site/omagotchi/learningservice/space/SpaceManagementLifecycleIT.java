package site.omagotchi.learningservice.space;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.occupancy.support.OccupancyTestFixture;
import site.omagotchi.learningservice.space.application.SpaceCommandService;
import site.omagotchi.learningservice.space.application.SpaceErrorCode;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공간 관리 주체의 생애주기 (CE-04, RM-16, RM-25, RM-26).
 *
 * <p><b>세 규칙이 맞물려야 순환이 닫힌다.</b> 기수 종료가 관리 주체를 풀고(CE-04), 주체 없는
 * 공간은 기수 매니저 누구나 관리하되 삭제만 막히고(RM-16·RM-25), 삭제하려는 기수가 인수한다.
 * 하나만 빠져도 공간이 갇힌다 — 해제가 없으면 종료 기수를 가리킨 채 <b>수정·비활성까지
 * 영구히 막히고</b>, 인수가 없으면 <b>어떤 공간도 다시 삭제되지 않아 무한히 누적된다.</b></p>
 *
 * <p>세 규칙이 서로의 전제라 단위 테스트로는 이 성질이 드러나지 않는다. 실제 기수 종료
 * 훅과 DB를 태워야 순환이 닫히는지 확인된다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, OccupancyTestFixture.class})
class SpaceManagementLifecycleIT {

    @Autowired
    OccupancyTestFixture fixture;

    @Autowired
    CohortService cohortService;

    @Autowired
    SpaceCommandService spaceCommandService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    VacancyAlertSender vacancyAlertSender;

    /**
     * <b>유형을 가리지 않는 것이 이 단계의 핵심이다.</b> 실습실만 풀고 멈추면 회의실·독서실이
     * 종료된 기수를 가리킨 채 남아, 그 기수의 ACTIVE 매니저가 존재하지 않으므로 수정·활성화·
     * 비활성화·삭제가 전부 403이 된다 — 공사로 못 쓰게 된 회의실을 아무도 내릴 수 없다.
     */
    @Test
    @DisplayName("기수 종료는 회의실·독서실·실습실의 관리 주체를 모두 해제한다.")
    void cohortEndReleasesManagementOfEverySpaceType() {
        Long cohortId = fixture.createCohort("공간생애-전유형");
        fixture.createActiveMember(cohortId);
        Long roomId = fixture.createMeetingRoom(cohortId, "공간생애-전유형-회의실", 8);
        Long studyId = fixture.createStudyRoom(cohortId, "공간생애-전유형-독서실", 20);
        Long labId = fixture.createLab(cohortId, "공간생애-전유형-실습실", 30);

        closeCohort(cohortId);

        awaitUntil(() -> cohortOf(roomId) == null, "회의실 관리 주체가 해제되지 않았습니다");
        assertThat(cohortOf(studyId)).isNull();
        assertThat(cohortOf(labId)).isNull();
    }

    /**
     * 주체 없는 공간의 권한 경계 — 관리는 열고 삭제는 막는다. 되돌릴 수 없는 행위만 소유를
     * 요구하는 것이 RM-25의 취지이고, 그래서 다음 단계(인수)가 필요해진다.
     */
    @Test
    @DisplayName("주체 없는 공간은 다른 기수 매니저가 비활성화할 수 있지만 삭제는 못 한다.")
    void unmanagedSpaceIsManageableButNotDeletable() {
        Long endedCohortId = fixture.createCohort("공간생애-경계-종료기수");
        fixture.createActiveMember(endedCohortId);
        Long roomId = fixture.createMeetingRoom(endedCohortId, "공간생애-경계-회의실", 8);

        Long otherCohortId = fixture.createCohort("공간생애-경계-타기수");
        OccupancyTestFixture.Member otherManager = fixture.createActiveMember(otherCohortId);
        activate(otherCohortId);

        closeCohort(endedCohortId);
        awaitUntil(() -> cohortOf(roomId) == null, "관리 주체가 해제되지 않았습니다");

        assertThatCode(() ->
                spaceCommandService.deactivate(roomId, "기수 종료 후 정리", otherManager.userId()))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> spaceCommandService.delete(roomId, otherManager.userId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(SpaceErrorCode.UNMANAGED_SPACE_DELETE_NOT_ALLOWED));
    }

    /**
     * <b>인수가 삭제 경로를 되살린다.</b> 예전에는 실습실만 배정 가능해서, 기수 종료로 주체가
     * 풀린 회의실·독서실은 영구히 삭제할 수 없었다. 인수는 "내가 책임지고 정리한다"는 선언이고
     * {@code cohort_id}에 기록되므로 누가 지웠는지가 남는다.
     */
    @Test
    @DisplayName("다른 기수가 회의실을 인수하면 삭제까지 할 수 있다.")
    void claimingSpaceRestoresDeletePath() {
        Long endedCohortId = fixture.createCohort("공간생애-인수-종료기수");
        fixture.createActiveMember(endedCohortId);
        Long roomId = fixture.createMeetingRoom(endedCohortId, "공간생애-인수-회의실", 8);

        Long newCohortId = fixture.createCohort("공간생애-인수-신기수");
        OccupancyTestFixture.Member newManager = fixture.createActiveMember(newCohortId);
        activate(newCohortId);

        closeCohort(endedCohortId);
        awaitUntil(() -> cohortOf(roomId) == null, "관리 주체가 해제되지 않았습니다");

        spaceCommandService.assignCohort(roomId, newCohortId, newManager.userId());
        assertThat(cohortOf(roomId)).isEqualTo(newCohortId);

        spaceCommandService.deactivate(roomId, "인수 후 폐기", newManager.userId());
        spaceCommandService.delete(roomId, newManager.userId());

        assertThat(deletedAt(roomId)).isNotNull();
    }

    /**
     * 순환이 닫히는지 — 인수한 기수가 다시 종료되면 주체가 또 풀려야 한다. 이것이
     * 성립하지 않으면 인수는 동결을 다음 기수로 미루는 것에 불과하다.
     */
    @Test
    @DisplayName("인수한 기수가 종료되면 관리 주체가 다시 풀린다.")
    void managementCyclesBackToUnassignedOnNextCohortEnd() {
        Long firstCohortId = fixture.createCohort("공간생애-순환-1기");
        fixture.createActiveMember(firstCohortId);
        Long roomId = fixture.createMeetingRoom(firstCohortId, "공간생애-순환-회의실", 8);

        Long secondCohortId = fixture.createCohort("공간생애-순환-2기");
        OccupancyTestFixture.Member secondManager = fixture.createActiveMember(secondCohortId);
        activate(secondCohortId);

        closeCohort(firstCohortId);
        awaitUntil(() -> cohortOf(roomId) == null, "1기 종료로 해제되지 않았습니다");

        spaceCommandService.assignCohort(roomId, secondCohortId, secondManager.userId());

        cohortService.changeStatus(secondCohortId,
                new ChangeCohortStatusCommand(CohortStatus.CLOSED), GlobalRole.SYSTEM_ADMIN);

        awaitUntil(() -> cohortOf(roomId) == null, "2기 종료로 다시 해제되지 않았습니다");
    }

    /** 관리 주체는 하나여야 한다 — 유형이 넓어져도 이 배타성은 유지된다. */
    @Test
    @DisplayName("이미 주체가 있는 회의실은 다른 기수가 인수할 수 없다.")
    void cannotClaimSpaceThatAlreadyHasManagingCohort() {
        Long ownerCohortId = fixture.createCohort("공간생애-배타-소유기수");
        fixture.createActiveMember(ownerCohortId);
        Long roomId = fixture.createMeetingRoom(ownerCohortId, "공간생애-배타-회의실", 8);

        Long otherCohortId = fixture.createCohort("공간생애-배타-타기수");
        OccupancyTestFixture.Member otherManager = fixture.createActiveMember(otherCohortId);
        activate(otherCohortId);

        assertThatThrownBy(() ->
                spaceCommandService.assignCohort(roomId, otherCohortId, otherManager.userId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(SpaceErrorCode.SPACE_ALREADY_ASSIGNED));
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void activate(Long cohortId) {
        fixture.createLab(
                cohortId,
                "공간생애-활성화-실습실-" + cohortId,
                1
        );
        cohortService.changeStatus(cohortId,
                new ChangeCohortStatusCommand(CohortStatus.ACTIVE), GlobalRole.SYSTEM_ADMIN);
    }

    private void closeCohort(Long cohortId) {
        activate(cohortId);
        cohortService.changeStatus(cohortId,
                new ChangeCohortStatusCommand(CohortStatus.CLOSED), GlobalRole.SYSTEM_ADMIN);
    }

    private void awaitUntil(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError(message);
    }

    private Long cohortOf(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT cohort_id FROM learning_service.spaces WHERE id = ?
                """, Long.class, spaceId);
    }

    private Object deletedAt(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT deleted_at FROM learning_service.spaces WHERE id = ?
                """, Object.class, spaceId);
    }
}
