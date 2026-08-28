package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.command.AssignCohortManagerCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateCohortCommand;
import site.omagotchi.learningservice.cohort.application.command.IssueJoinCodeCommand;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortJoinCodeRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("가입 코드 서비스 통합 테스트")
class JoinCodeServiceIT {

    private static final UUID SYSTEM_ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID MANAGER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Autowired CohortService cohortService;
    @Autowired CohortManagerService cohortManagerService;
    @Autowired JoinCodeService joinCodeService;
    @Autowired CohortJoinCodeRepository joinCodeRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("기수 매니저는 만료 전 재발급할 수 없고 만료 후 새 코드를 발급할 수 있다")
    void reissuesJoinCodeOnlyAfterExistingCodeExpires() {
        LocalDate today = LocalDate.now();
        var cohort = cohortService.create(
                new CreateCohortCommand(
                        "가입 코드 재발급 검증 기수",
                        "활성 코드 교체 검증",
                        today.minusDays(1),
                        today.plusMonths(1)
                ),
                SYSTEM_ADMIN_ID,
                GlobalRole.SYSTEM_ADMIN
        );
        cohortManagerService.assignManager(
                cohort.id(),
                new AssignCohortManagerCommand(MANAGER_ID),
                SYSTEM_ADMIN_ID,
                GlobalRole.SYSTEM_ADMIN
        );

        var first = joinCodeService.issue(
                cohort.id(),
                new IssueJoinCodeCommand(OffsetDateTime.now().plusDays(1)),
                MANAGER_ID
        );

        BusinessException conflict = assertThrows(
                BusinessException.class,
                () -> joinCodeService.issue(
                        cohort.id(),
                        new IssueJoinCodeCommand(OffsetDateTime.now().plusDays(2)),
                        MANAGER_ID
                )
        );
        assertSame(CohortErrorCode.JOIN_CODE_ALREADY_EXISTS, conflict.getErrorCode());

        joinCodeService.revoke(cohort.id(), MANAGER_ID);
        assertEquals(
                CohortJoinCodeStatus.REVOKED,
                joinCodeService.getLatestJoinCode(cohort.id(), MANAGER_ID).status()
        );
        BusinessException revokedConflict = assertThrows(
                BusinessException.class,
                () -> joinCodeService.issue(
                        cohort.id(),
                        new IssueJoinCodeCommand(OffsetDateTime.now().plusDays(2)),
                        MANAGER_ID
                )
        );
        assertSame(CohortErrorCode.JOIN_CODE_ALREADY_EXISTS, revokedConflict.getErrorCode());

        jdbcTemplate.update(
                """
                update learning_service.cohort_join_codes
                   set expires_at = ?
                 where code_hash = ?
                """,
                OffsetDateTime.now().minusMinutes(1),
                JoinCodeHash.sha256(first.code())
        );

        OffsetDateTime secondExpiry = OffsetDateTime.now().plusDays(2);
        var second = joinCodeService.issue(
                cohort.id(),
                new IssueJoinCodeCommand(secondExpiry),
                MANAGER_ID
        );
        var activeCode = joinCodeService.getLatestJoinCode(cohort.id(), MANAGER_ID);

        assertNotNull(first.code());
        assertNotNull(second.code());
        assertNotEquals(first.code(), second.code());
        assertEquals(
                CohortJoinCodeStatus.REVOKED,
                joinCodeRepository.findByCodeHash(JoinCodeHash.sha256(first.code()))
                        .orElseThrow()
                        .getStatus()
        );
        assertEquals(
                1,
                joinCodeRepository.findAll().stream()
                        .filter(joinCode -> joinCode.getCohortId().equals(cohort.id()))
                        .filter(joinCode -> joinCode.getStatus() == CohortJoinCodeStatus.ACTIVE)
                        .count()
        );
        assertEquals(
                secondExpiry.toInstant().truncatedTo(ChronoUnit.MILLIS),
                activeCode.expiresAt().toInstant().truncatedTo(ChronoUnit.MILLIS)
        );
        assertEquals(
                second.issuedAt().toInstant().truncatedTo(ChronoUnit.MILLIS),
                activeCode.issuedAt().toInstant().truncatedTo(ChronoUnit.MILLIS)
        );
    }
}
