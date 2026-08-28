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
import site.omagotchi.learningservice.cohort.application.port.JoinCodePersistence;
import site.omagotchi.learningservice.cohort.application.result.IssuedJoinCodeResponse;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("가입 코드 서비스 통합 테스트")
class JoinCodeServiceIT {

    private static final UUID SYSTEM_ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Autowired CohortService cohortService;
    @Autowired CohortManagerService cohortManagerService;
    @Autowired JoinCodeService joinCodeService;
    @Autowired JoinCodePersistence joinCodePersistence;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("만료 전에는 ACTIVE 코드와 수동 폐기 코드 모두 재발급할 수 없다")
    void blocksReissueBeforeExpiryEvenAfterManualRevocation() {
        ManagedCohort fixture = createManagedCohort("만료 전 재발급 차단");
        joinCodeService.issue(
                fixture.cohortId(),
                new IssueJoinCodeCommand(OffsetDateTime.now().plusDays(1)),
                fixture.managerId()
        );

        assertAlreadyExists(fixture);

        joinCodeService.revoke(fixture.cohortId(), fixture.managerId());
        assertEquals(
                CohortJoinCodeStatus.REVOKED,
                joinCodeService.getLatestJoinCode(fixture.cohortId(), fixture.managerId()).status()
        );
        assertAlreadyExists(fixture);
    }

    @Test
    @DisplayName("만료된 ACTIVE 코드는 폐기한 뒤 새 코드로 교체하고 ACTIVE 하나만 유지한다")
    void replacesExpiredActiveCodeWithOneNewActiveCode() {
        ManagedCohort fixture = createManagedCohort("만료 ACTIVE 코드 교체");
        var first = joinCodeService.issue(
                fixture.cohortId(),
                new IssueJoinCodeCommand(OffsetDateTime.now().plusDays(1)),
                fixture.managerId()
        );

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
                fixture.cohortId(),
                new IssueJoinCodeCommand(secondExpiry),
                fixture.managerId()
        );
        var activeCode = joinCodeService.getLatestJoinCode(fixture.cohortId(), fixture.managerId());

        assertNotNull(first.code());
        assertNotNull(second.code());
        assertNotEquals(first.code(), second.code());
        assertEquals(
                CohortJoinCodeStatus.REVOKED,
                joinCodePersistence.findByCodeHash(JoinCodeHash.sha256(first.code()))
                        .orElseThrow()
                        .getStatus()
        );
        assertEquals(1L, countActiveCodes(fixture.cohortId()));
        assertEquals(
                secondExpiry.toInstant().truncatedTo(ChronoUnit.MICROS),
                activeCode.expiresAt().toInstant().truncatedTo(ChronoUnit.MICROS)
        );
        assertEquals(
                second.issuedAt().toInstant().truncatedTo(ChronoUnit.MICROS),
                activeCode.issuedAt().toInstant().truncatedTo(ChronoUnit.MICROS)
        );
    }

    @Test
    @DisplayName("같은 기수에 동시 발급하면 하나만 성공하고 나머지는 이미 존재 오류가 된다")
    void concurrentIssuesAllowOnlyOneCode() throws Exception {
        ManagedCohort fixture = createManagedCohort("가입 코드 동시 발급");
        int requestCount = 8;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<Object>> futures = new ArrayList<>();
        OffsetDateTime expiry = OffsetDateTime.now().plusDays(1);

        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 발급 시작 신호를 기다리지 못했습니다.");
                    }
                    try {
                        return joinCodeService.issue(
                                fixture.cohortId(),
                                new IssueJoinCodeCommand(expiry),
                                fixture.managerId()
                        );
                    } catch (BusinessException exception) {
                        return exception;
                    }
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }

            assertEquals(
                    1,
                    outcomes.stream().filter(IssuedJoinCodeResponse.class::isInstance).count()
            );
            List<BusinessException> conflicts = outcomes.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .toList();
            assertEquals(requestCount - 1, conflicts.size());
            conflicts.forEach(conflict ->
                    assertSame(CohortErrorCode.JOIN_CODE_ALREADY_EXISTS, conflict.getErrorCode()));
            assertEquals(1L, countActiveCodes(fixture.cohortId()));
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertAlreadyExists(ManagedCohort fixture) {
        BusinessException conflict = assertThrows(
                BusinessException.class,
                () -> joinCodeService.issue(
                        fixture.cohortId(),
                        new IssueJoinCodeCommand(OffsetDateTime.now().plusDays(2)),
                        fixture.managerId()
                )
        );
        assertSame(CohortErrorCode.JOIN_CODE_ALREADY_EXISTS, conflict.getErrorCode());
    }

    private long countActiveCodes(Long cohortId) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from learning_service.cohort_join_codes
                 where cohort_id = ?
                   and status = 'ACTIVE'
                """,
                Long.class,
                cohortId
        );
        return count == null ? 0L : count;
    }

    private ManagedCohort createManagedCohort(String name) {
        LocalDate today = LocalDate.now();
        UUID managerId = UUID.randomUUID();
        var cohort = cohortService.create(
                new CreateCohortCommand(
                        name + " " + UUID.randomUUID(),
                        "가입 코드 정책 통합 검증",
                        today.minusDays(1),
                        today.plusMonths(1)
                ),
                SYSTEM_ADMIN_ID,
                GlobalRole.SYSTEM_ADMIN
        );
        cohortManagerService.assignManager(
                cohort.id(),
                new AssignCohortManagerCommand(managerId),
                SYSTEM_ADMIN_ID,
                GlobalRole.SYSTEM_ADMIN
        );
        return new ManagedCohort(cohort.id(), managerId);
    }

    private record ManagedCohort(Long cohortId, UUID managerId) {
    }
}
