package site.omagotchi.learningservice.space.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.SpaceCommandService;
import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.contains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(
        username = "00000000-0000-0000-0000-000000000092",
        roles = "USER"
)
@ActiveProfiles("test")
@Import({
        TestcontainersConfiguration.class,
        SpaceIT.FixedClockConfiguration.class
})
@Transactional
class SpaceIT {

    private static final UUID MANAGER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000092"
    );

    private static final Instant NOW =
            Instant.parse("2026-07-28T05:00:00Z");
    private static final OffsetDateTime OFFSET_NOW =
            OffsetDateTime.parse("2026-07-28T14:00:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpaceCommandService spaceCommandService;

    @Test
    void returnsNonDeletedActiveMeetingAsAvailable() throws Exception {
        Long spaceId = insertSpace(
                "통합 조회 가능한 회의실",
                "ACTIVE",
                null
        );

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].operationalStatus"
                                .formatted(spaceId)
                ).value(contains("ACTIVE")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].status".formatted(spaceId)
                ).value(contains("AVAILABLE")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupancyExpiresAt"
                                .formatted(spaceId)
                ).value(contains((Object) null)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].remainingTimeSeconds"
                                .formatted(spaceId)
                ).value(contains((Object) null)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupiedBySameCohort"
                                .formatted(spaceId)
                ).value(contains(false)));
    }

    @Test
    void excludesSoftDeletedSpace() throws Exception {
        Long spaceId = insertSpace(
                "통합 삭제된 회의실",
                "ACTIVE",
                OFFSET_NOW
        );

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)]".formatted(spaceId)
                ).isEmpty());
    }

    @Test
    void returnsActiveOccupancyAsOccupiedWithStableRemainingTime()
            throws Exception {
        Long spaceId = insertSpace(
                "통합 점유 중 회의실",
                "ACTIVE",
                null
        );
        insertOccupancy(
                spaceId,
                "ACTIVE",
                OFFSET_NOW.minusMinutes(10),
                OFFSET_NOW.plusMinutes(30),
                null
        );

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].status".formatted(spaceId)
                ).value(contains("OCCUPIED")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupancyExpiresAt"
                                .formatted(spaceId)
                ).value(contains("2026-07-28T14:30:00+09:00")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].remainingTimeSeconds"
                                .formatted(spaceId)
                ).value(contains(1800)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupiedBySameCohort"
                                .formatted(spaceId)
                ).value(contains(false)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupancyCohortId"
                                .formatted(spaceId)
                ).value(contains((Object) null)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupierMembershipId"
                                .formatted(spaceId)
                ).value(contains((Object) null)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupierUserId"
                                .formatted(spaceId)
                ).value(contains((Object) null)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].participantUserIds"
                                .formatted(spaceId)
                ).value(contains((Object) null)));
    }

    @Test
    void sameCohortRequesterReceivesOccupierAndParticipantDetails()
            throws Exception {
        UUID requesterUserId = UUID.randomUUID();
        Long cohortId = insertCohortWithManager(requesterUserId);
        activateCohort(cohortId);
        Long spaceId = insertSpace(
                "통합 동일 기수 점유 회의실",
                "ACTIVE",
                null
        );
        OccupancyFixture occupancy = insertOccupancyWithDetails(
                spaceId,
                cohortId
        );

        mockMvc.perform(get("/api/v1/spaces")
                        .with(jwt().jwt(token -> token
                                .subject(requesterUserId.toString())
                                .claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].status".formatted(spaceId)
                ).value(contains("OCCUPIED")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupiedBySameCohort"
                                .formatted(spaceId)
                ).value(contains(true)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].remainingTimeSeconds"
                                .formatted(spaceId)
                ).value(contains(1800)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupancyCohortId"
                                .formatted(spaceId)
                ).value(contains(cohortId.intValue())))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupierMembershipId"
                                .formatted(spaceId)
                ).value(contains(occupancy.membershipId().intValue())))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupierUserId"
                                .formatted(spaceId)
                ).value(contains(occupancy.occupierUserId().toString())))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].participantUserIds[0]"
                                .formatted(spaceId)
                ).value(contains(occupancy.participantUserId().toString())));
    }

    @Test
    void otherCohortRequesterReceivesOccupancyWithoutPersonalDetails()
            throws Exception {
        UUID requesterUserId = UUID.randomUUID();
        Long requesterCohortId = insertCohortWithManager(requesterUserId);
        Long occupierCohortId = insertCohortWithManager(UUID.randomUUID());
        activateCohort(requesterCohortId);
        activateCohort(occupierCohortId);
        Long spaceId = insertSpace(
                "통합 타 기수 점유 회의실",
                "ACTIVE",
                null
        );
        insertOccupancyWithDetails(spaceId, occupierCohortId);

        mockMvc.perform(get("/api/v1/spaces")
                        .with(jwt().jwt(token -> token
                                .subject(requesterUserId.toString())
                                .claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].status".formatted(spaceId)
                ).value(contains("OCCUPIED")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupiedBySameCohort"
                                .formatted(spaceId)
                ).value(contains(false)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupancyExpiresAt"
                                .formatted(spaceId)
                ).value(contains("2026-07-28T14:30:00+09:00")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].remainingTimeSeconds"
                                .formatted(spaceId)
                ).value(contains(1800)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupancyCohortId"
                                .formatted(spaceId)
                ).value(contains((Object) null)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupierMembershipId"
                                .formatted(spaceId)
                ).value(contains((Object) null)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupierUserId"
                                .formatted(spaceId)
                ).value(contains((Object) null)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].participantUserIds"
                                .formatted(spaceId)
                ).value(contains((Object) null)));
    }

    @Test
    void inactiveSpaceIncludesReasonAndIsUnavailable() throws Exception {
        Long spaceId = insertSpace(
                "통합 비활성 조회 회의실",
                "INACTIVE",
                null
        );
        jdbcTemplate.update("""
                UPDATE learning_service.spaces
                SET inactive_reason = '시설 점검'
                WHERE id = ?
                """, spaceId);

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].operationalStatus"
                                .formatted(spaceId)
                ).value(contains("INACTIVE")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].inactiveReason"
                                .formatted(spaceId)
                ).value(contains("시설 점검")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].status".formatted(spaceId)
                ).value(contains("UNAVAILABLE")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupiedBySameCohort"
                                .formatted(spaceId)
                ).value(contains(false)));
    }

    @Test
    void labAndStudyRemainNotApplicableAndNotOccupiedBySameCohort()
            throws Exception {
        Long labId = insertTypedSpace(
                "통합 조회 실습실",
                "LAB",
                "ACTIVE",
                null,
                null
        );
        Long studyId = insertTypedSpace(
                "통합 조회 학습 공간",
                "STUDY",
                "ACTIVE",
                null,
                null
        );

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].status".formatted(labId)
                ).value(contains("NOT_APPLICABLE")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupiedBySameCohort"
                                .formatted(labId)
                ).value(contains(false)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].status".formatted(studyId)
                ).value(contains("NOT_APPLICABLE")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupiedBySameCohort"
                                .formatted(studyId)
                ).value(contains(false)));
    }

    @Test
    void ignoresEndedOccupancy() throws Exception {
        Long spaceId = insertSpace(
                "통합 종료된 점유 회의실",
                "ACTIVE",
                null
        );
        insertOccupancy(
                spaceId,
                "RELEASED",
                OFFSET_NOW.minusMinutes(30),
                OFFSET_NOW.plusMinutes(30),
                OFFSET_NOW.minusMinutes(5)
        );

        assertAvailable(spaceId);
    }

    @Test
    void ignoresExpiredOccupancy() throws Exception {
        Long spaceId = insertSpace(
                "통합 만료된 점유 회의실",
                "ACTIVE",
                null
        );
        insertOccupancy(
                spaceId,
                "ACTIVE",
                OFFSET_NOW.minusMinutes(30),
                OFFSET_NOW.minusMinutes(1),
                null
        );

        assertAvailable(spaceId);
    }

    @Test
    void createsSpaceAndReturnsItFromQueryApi() throws Exception {
        String name = "통합 생성 회의실";
        Long cohortId = insertManagementCohort();

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(name, 6, cohortId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.type").value("MEETING"))
                .andExpect(jsonPath("$.capacity").value(6))
                .andExpect(jsonPath("$.cohortId").value(cohortId));

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.name == '%s')].operationalStatus"
                                .formatted(name)
                ).value(contains("INACTIVE")))
                .andExpect(jsonPath(
                        "$[?(@.name == '%s')].status".formatted(name)
                ).value(contains("UNAVAILABLE")));

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(jsonPath(
                        "$[?(@.name == '%s')].cohortId".formatted(name)
                ).value(contains(cohortId.intValue())));
    }

    @Test
    void createsSpaceWithActorsActiveCohortWhenCohortIdIsOmitted()
            throws Exception {
        Long cohortId = insertManagementCohort();
        jdbcTemplate.update("""
                UPDATE learning_service.cohorts
                SET status = 'ACTIVE'
                WHERE id = ?
                """, cohortId);

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"통합 자동 기수 공간",
                                  "type":"STUDY",
                                  "capacity":12
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cohortId").value(cohortId))
                .andExpect(jsonPath("$.type").value("STUDY"));
    }

    @Test
    void rejectsCreateWithoutActiveManagedCohort() throws Exception {
        insertManagementCohort();

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"통합 활성 기수 없는 생성",
                                  "type":"STUDY",
                                  "capacity":12
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("ACTIVE_COHORT_NOT_FOUND"));
    }

    @Test
    void requiresCohortIdWhenManagerHasMultipleActiveCohorts()
            throws Exception {
        Long firstCohortId = insertManagementCohort();
        Long secondCohortId = insertManagementCohort();
        jdbcTemplate.update("""
                UPDATE learning_service.cohorts
                SET status = 'ACTIVE'
                WHERE id IN (?, ?)
                """, firstCohortId, secondCohortId);

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"통합 다중 활성 기수 공간",
                                  "type":"STUDY",
                                  "capacity":12
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_COHORT_ID_REQUIRED"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAssignmentsToSameLabAllowOnlyOneRequest()
            throws Exception {
        // 미배정 실습실은 기수 매니저 누구나 배정할 수 있다 (RM-16).
        // 두 요청이 서로 다른 기수를 노리므로 요청자는 두 기수 모두의 매니저여야 한다.
        UUID managerId = UUID.randomUUID();
        Long firstCohortId = insertCohortWithManager(managerId);
        Long secondCohortId = insertCohortWithManager(managerId);
        Long labId = insertTypedSpace(
                "통합 동시 배정 실습실 " + UUID.randomUUID(),
                "LAB",
                "INACTIVE",
                null,
                null
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> first = executor.submit(() -> assignAfterSignal(
                    labId,
                    firstCohortId,
                    managerId,
                    ready,
                    start
            ));
            Future<String> second = executor.submit(() -> assignAfterSignal(
                    labId,
                    secondCohortId,
                    managerId,
                    ready,
                    start
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            ))
                    .containsExactlyInAnyOrder(
                            "SUCCESS",
                            "SPACE_ALREADY_ASSIGNED"
                    );
            assertThat(readCohortId(labId))
                    .isIn(firstCohortId, secondCohortId);
        } finally {
            executor.shutdownNow();
            cleanupCommittedFixtures(
                    List.of(labId),
                    List.of(firstCohortId, secondCohortId)
            );
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deleteAndActivateDoNotCreateDeletedActiveSpace()
            throws Exception {
        UUID managerId = UUID.randomUUID();
        Long cohortId = insertCohortWithManager(managerId);
        Long spaceId = insertTypedSpace(
                "통합 삭제 활성화 경쟁 " + UUID.randomUUID(),
                "MEETING",
                "INACTIVE",
                cohortId,
                null
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> deletion = executor.submit(() ->
                    deleteAfterSignal(
                            spaceId,
                            managerId,
                            ready,
                            start
                    ));
            Future<String> activation = executor.submit(() ->
                    activateAfterSignal(
                            spaceId,
                            managerId,
                            ready,
                            start
                    ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            String deleteResult = deletion.get(10, TimeUnit.SECONDS);
            String activateResult = activation.get(10, TimeUnit.SECONDS);
            SpaceRow finalState = readSpaceRow(spaceId);

            if ("DELETE_SUCCESS".equals(deleteResult)) {
                assertThat(activateResult)
                        .isEqualTo("SPACE_NOT_FOUND");
                assertThat(finalState.status()).isEqualTo("INACTIVE");
                assertThat(finalState.deletedAt()).isNotNull();
            } else {
                assertThat(deleteResult)
                        .isEqualTo("SPACE_ACTIVE_DELETE_NOT_ALLOWED");
                assertThat(activateResult).isEqualTo("ACTIVATE_SUCCESS");
                assertThat(finalState.status()).isEqualTo("ACTIVE");
                assertThat(finalState.deletedAt()).isNull();
            }

            assertThat(finalState.status().equals("ACTIVE")
                    && finalState.deletedAt() != null).isFalse();
        } finally {
            executor.shutdownNow();
            cleanupCommittedFixtures(
                    List.of(spaceId),
                    List.of(cohortId)
            );
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deleteAndUpdateDoNotReviveDeletedSpace()
            throws Exception {
        UUID managerId = UUID.randomUUID();
        Long cohortId = insertCohortWithManager(managerId);
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String originalName = "삭제수정-" + uniqueSuffix;
        String updatedName = originalName + "-수정";
        Long spaceId = insertTypedSpace(
                originalName,
                "MEETING",
                "INACTIVE",
                cohortId,
                null
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> deletion = executor.submit(() ->
                    deleteAfterSignal(
                            spaceId,
                            managerId,
                            ready,
                            start
                    ));
            Future<String> update = executor.submit(() ->
                    updateAfterSignal(
                            spaceId,
                            updatedName,
                            managerId,
                            ready,
                            start
                    ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            String deleteResult = deletion.get(10, TimeUnit.SECONDS);
            String updateResult = update.get(10, TimeUnit.SECONDS);
            SpaceRow finalState = readSpaceRow(spaceId);

            assertThat(deleteResult).isEqualTo("DELETE_SUCCESS");
            assertThat(updateResult)
                    .isIn("UPDATE_SUCCESS", "SPACE_NOT_FOUND");
            assertThat(finalState.deletedAt()).isNotNull();

            if ("UPDATE_SUCCESS".equals(updateResult)) {
                assertThat(finalState.name()).isEqualTo(updatedName);
            } else {
                assertThat(finalState.name()).isEqualTo(originalName);
            }
        } finally {
            executor.shutdownNow();
            cleanupCommittedFixtures(
                    List.of(spaceId),
                    List.of(cohortId)
            );
        }
    }

    @Test
    void rejectsZeroCapacityWithBadRequest() throws Exception {
        Long cohortId = insertManagementCohort();
        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("통합 잘못된 정원", 0, cohortId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void rejectsDuplicateNameDifferingOnlyByCaseWithConflict() throws Exception {
        String originalName = "Meeting Room A";
        String duplicateName = "meeting room a";
        Long cohortId = insertManagementCohort();

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(originalName, 6, cohortId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(duplicateName, 6, cohortId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_DUPLICATE_NAME"));

        Long activeSpaceCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM learning_service.spaces
                WHERE LOWER(BTRIM(name)) = LOWER(BTRIM(?))
                  AND deleted_at IS NULL
                """, Long.class, originalName);
        assertThat(activeSpaceCount).isEqualTo(1L);
    }

    @Test
    void rejectsSpaceCreationByActiveNonManager() throws Exception {
        Long cohortId = insertManagementCohort();
        UUID studentId = UUID.randomUUID();
        insertActiveMembership(cohortId, studentId, "STUDENT");

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .with(user(studentId.toString()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(
                                "통합 비관리자 생성 차단",
                                6,
                                cohortId
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));
    }

    @Test
    void rejectsAnotherCohortOnSpaceCreationWithForbidden() throws Exception {
        Long cohortId = insertManagementCohort();

        mockMvc.perform(post("/api/v1/admin/spaces")
                        .with(user(UUID.randomUUID().toString()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(
                                "통합 비소속 생성 차단",
                                6,
                                cohortId
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
    }

    @Test
    void activatesInactiveSpaceAndClearsReason() throws Exception {
        Long spaceId = insertSpace(
                "통합 활성화 회의실",
                "INACTIVE",
                null
        );
        jdbcTemplate.update("""
                UPDATE learning_service.spaces
                SET inactive_reason = '정기 점검'
                WHERE id = ?
                """, spaceId);
        assignManagementCohort(spaceId);

        mockMvc.perform(post(
                        "/api/v1/admin/spaces/{space-id}/activate",
                        spaceId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.inactiveReason").isEmpty());
    }

    @Test
    void deactivatesActiveSpaceAndPersistsTrimmedReason() throws Exception {
        Long spaceId = insertSpace(
                "통합 비활성화 회의실",
                "ACTIVE",
                null
        );
        assignManagementCohort(spaceId);

        mockMvc.perform(post(
                        "/api/v1/admin/spaces/{space-id}/deactivate",
                        spaceId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":"  냉방 점검  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.inactiveReason").value("냉방 점검"));
    }

    @Test
    void rejectsDeactivationWhenActiveOccupancyExists() throws Exception {
        Long spaceId = insertSpace(
                "통합 점유 비활성화 차단 회의실",
                "ACTIVE",
                null
        );
        insertOccupancy(
                spaceId,
                "ACTIVE",
                OFFSET_NOW.minusMinutes(10),
                OFFSET_NOW.plusMinutes(30),
                null
        );
        assignManagementCohort(spaceId);

        mockMvc.perform(post(
                        "/api/v1/admin/spaces/{space-id}/deactivate",
                        spaceId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":"점검"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACTIVE_OCCUPANCY_EXISTS"));
    }

    @Test
    void deactivatesWhenOccupancyIsReleased() throws Exception {
        Long spaceId = insertSpace(
                "통합 종료 점유 비활성화 허용",
                "ACTIVE",
                null
        );
        insertOccupancy(
                spaceId,
                "RELEASED",
                OFFSET_NOW.minusMinutes(30),
                OFFSET_NOW.plusMinutes(30),
                OFFSET_NOW.minusMinutes(1)
        );

        assertDeactivationSucceeds(spaceId);
    }

    @Test
    void deactivatesWhenActiveOccupancyExpiredBeforeOrAtNow()
            throws Exception {
        Long expiredSpaceId = insertSpace(
                "통합 만료 점유 비활성화 허용",
                "ACTIVE",
                null
        );
        Long expiresNowSpaceId = insertSpace(
                "통합 현재 만료 점유 비활성화 허용",
                "ACTIVE",
                null
        );
        insertOccupancy(
                expiredSpaceId,
                "ACTIVE",
                OFFSET_NOW.minusMinutes(30),
                OFFSET_NOW.minusSeconds(1),
                null
        );
        insertOccupancy(
                expiresNowSpaceId,
                "ACTIVE",
                OFFSET_NOW.minusMinutes(30),
                OFFSET_NOW,
                null
        );

        assertDeactivationSucceeds(expiredSpaceId);
        assertDeactivationSucceeds(expiresNowSpaceId);
    }

    @Test
    void activeOccupancyOfAnotherSpaceDoesNotBlockDeactivation()
            throws Exception {
        Long occupiedSpaceId = insertSpace(
                "통합 다른 점유 공간",
                "ACTIVE",
                null
        );
        Long targetSpaceId = insertSpace(
                "통합 비점유 대상 공간",
                "ACTIVE",
                null
        );
        insertOccupancy(
                occupiedSpaceId,
                "ACTIVE",
                OFFSET_NOW.minusMinutes(10),
                OFFSET_NOW.plusMinutes(10),
                null
        );

        assertDeactivationSucceeds(targetSpaceId);
    }

    @Test
    void updatesInactiveSpaceAndRejectsActiveCapacityReduction()
            throws Exception {
        Long inactiveId = insertSpace(
                "통합 수정 전 공간",
                "INACTIVE",
                null
        );
        Long activeId = insertSpace(
                "통합 활성 정원 축소 차단",
                "ACTIVE",
                null
        );
        assignManagementCohort(inactiveId);
        assignManagementCohort(activeId);

        mockMvc.perform(put("/api/v1/admin/spaces/{space-id}", inactiveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"  통합 수정 후 공간  ",
                                  "type":"STUDY",
                                  "capacity":4
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("통합 수정 후 공간"))
                .andExpect(jsonPath("$.type").value("STUDY"))
                .andExpect(jsonPath("$.capacity").value(4));

        mockMvc.perform(put("/api/v1/admin/spaces/{space-id}", activeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"통합 활성 정원 축소 차단",
                                  "type":"MEETING",
                                  "capacity":4
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(
                        "SPACE_ACTIVE_CAPACITY_REDUCTION_NOT_ALLOWED"
                ));
    }

    @Test
    void softDeletesInactiveSpaceAndAllowsReusingItsName() throws Exception {
        String name = "통합 삭제 후 재사용 공간";
        Long spaceId = insertSpace(name, "INACTIVE", null);
        assignManagementCohort(spaceId);

        mockMvc.perform(delete("/api/v1/admin/spaces/{space-id}", spaceId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)]".formatted(spaceId)
                ).isEmpty());

        Long cohortId = insertManagementCohort();
        mockMvc.perform(post("/api/v1/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(name, 6, cohortId)))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsActiveDeleteAndAllCommandsForAnotherCohortsSpace()
            throws Exception {
        Long activeSpaceId = insertSpace(
                "통합 활성 삭제 차단",
                "ACTIVE",
                null
        );
        assignManagementCohort(activeSpaceId);

        mockMvc.perform(delete(
                        "/api/v1/admin/spaces/{space-id}",
                        activeSpaceId
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACTIVE_DELETE_NOT_ALLOWED"));

        Long otherCohortId = insertCohortWithManager(UUID.randomUUID());
        Long otherSpaceId = insertTypedSpace(
                "통합 다른 기수 삭제 차단",
                "STUDY",
                "INACTIVE",
                otherCohortId,
                null
        );

        mockMvc.perform(put(
                        "/api/v1/admin/spaces/{space-id}",
                        otherSpaceId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"통합 다른 기수 수정 차단",
                                  "type":"STUDY",
                                  "capacity":10
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));

        mockMvc.perform(post(
                        "/api/v1/admin/spaces/{space-id}/activate",
                        otherSpaceId
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));

        mockMvc.perform(post(
                        "/api/v1/admin/spaces/{space-id}/deactivate",
                        otherSpaceId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":"점검"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));

        mockMvc.perform(delete(
                        "/api/v1/admin/spaces/{space-id}",
                        otherSpaceId
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));
    }

    @Test
    void rejectsDeletingInactiveSpaceWhenActiveOccupancyExists()
            throws Exception {
        Long spaceId = insertSpace(
                "통합 점유 중 비활성 공간 삭제",
                "INACTIVE",
                null
        );
        assignManagementCohort(spaceId);
        insertOccupancy(
                spaceId,
                "ACTIVE",
                OFFSET_NOW.minusMinutes(10),
                OFFSET_NOW.plusMinutes(30),
                null
        );

        mockMvc.perform(delete(
                        "/api/v1/admin/spaces/{space-id}",
                        spaceId
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACTIVE_OCCUPANCY_EXISTS"));

        assertThat(readSpaceRow(spaceId).deletedAt()).isNull();
    }

    @Test
    void assignsAndUnassignsLabCohort() throws Exception {
        Long cohortId = insertManagementCohort();
        Long labId = insertTypedSpace(
                "통합 배정 대상 실습실",
                "LAB",
                "INACTIVE",
                null,
                null
        );

        mockMvc.perform(put(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        labId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cohortId":%d}
                                """.formatted(cohortId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").value(cohortId))
                .andExpect(jsonPath("$.updatedAt")
                        .value("2026-07-28T14:00:00+09:00"));

        assertThat(readCohortId(labId)).isEqualTo(cohortId);

        mockMvc.perform(delete(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        labId
                ))
                .andExpect(status().isNoContent());

        assertThat(readCohortId(labId)).isNull();
    }

    @Test
    void rejectsInvalidCohortAssignmentStates() throws Exception {
        Long cohortId = insertManagementCohort();
        Long assignedLabId = insertTypedSpace(
                "통합 중복 배정 실습실",
                "LAB",
                "INACTIVE",
                cohortId,
                null
        );
        Long assignedMeetingId = insertTypedSpace(
                "통합 중복 배정 회의실",
                "MEETING",
                "INACTIVE",
                cohortId,
                null
        );
        Long deletedLabId = insertTypedSpace(
                "통합 삭제 실습실 배정 차단",
                "LAB",
                "INACTIVE",
                null,
                OFFSET_NOW
        );
        String request = """
                {"cohortId":%d}
                """.formatted(cohortId);

        mockMvc.perform(put(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        assignedLabId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ALREADY_ASSIGNED"));

        // 유형을 가리지 않는 것이 지금의 계약이다 — 이미 배정된 회의실도 실습실과 같은
        // 409다. 예전에는 "실습실이 아니라서" 400이었는데, 그 유형 제한을 폐기했다.
        mockMvc.perform(put(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        assignedMeetingId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ALREADY_ASSIGNED"));

        mockMvc.perform(put(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        deletedLabId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_NOT_FOUND"));

        mockMvc.perform(put(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        Long.MAX_VALUE
                ).contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));

        mockMvc.perform(put(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        assignedLabId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cohortId":9223372036854775807}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COHORT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidCohortUnassignmentStates() throws Exception {
        Long cohortId = insertManagementCohort();
        Long assignedMeetingId = insertTypedSpace(
                "통합 회의실 해제",
                "MEETING",
                "INACTIVE",
                cohortId,
                null
        );
        Long unassignedLabId = insertTypedSpace(
                "통합 미배정 해제 차단",
                "LAB",
                "INACTIVE",
                null,
                null
        );

        // 유형을 가리지 않는 것이 지금의 계약이다 — 배정된 회의실도 실습실처럼 해제할 수
        // 있다. 이 인수·해제 경로가 있어야 기수 종료로 주체가 풀린 회의실을 다른 기수가
        // 인수해 최종적으로 삭제할 수 있다 (RM-25, 명세 08 §2 4단계).
        mockMvc.perform(delete(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        assignedMeetingId
                ))
                .andExpect(status().isNoContent());
        assertThat(readCohortId(assignedMeetingId)).isNull();

        mockMvc.perform(delete(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        unassignedLabId
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_NOT_ASSIGNED"));
    }

    @Test
    void rejectsLabAssignmentChangesByAnotherCohortManager()
            throws Exception {
        Long otherCohortId = insertCohortWithManager(UUID.randomUUID());
        Long ownCohortId = insertManagementCohort();
        Long labId = insertTypedSpace(
                "통합 다른 기수 실습실",
                "LAB",
                "INACTIVE",
                otherCohortId,
                null
        );

        // 배정과 해제의 결과가 다르다.
        // 배정: 이미 배정된 실습실이면 대상 기수 매니저인 요청자는 누구든 409다 (명세 07
        //       §5). 소유 기수 권한을 먼저 보면 같은 상황이 요청자에 따라 403과 409로
        //       갈린다.
        // 해제: 소유 기수의 매니저만 할 수 있으므로 403이다 (명세 07 §5 "타 기수 실습실 해제").
        mockMvc.perform(put(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        labId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cohortId":%d}
                                """.formatted(ownCohortId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ALREADY_ASSIGNED"));

        mockMvc.perform(delete(
                        "/api/v1/admin/spaces/{space-id}/cohort",
                        labId
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));
    }

    @Test
    void anyCohortManagerCanManageSeedSpaceButNobodyCanDeleteIt()
            throws Exception {
        // 관리 주체가 없는 공간은 기수 매니저 누구나 관리할 수 있다 (RM-16).
        // 요청자가 어느 기수의 매니저이기만 하면 된다 — 그 기수와 공간은 무관하다.
        // 판정 기준은 공간 생성(cohortId 생략)과 같은 "활성 기수의 매니저"다.
        activateCohort(insertManagementCohort());
        Long seedSpaceId = insertSpace(
                "통합 시드 공간 관리",
                "INACTIVE",
                null
        );

        mockMvc.perform(post(
                        "/api/v1/admin/spaces/{space-id}/activate",
                        seedSpaceId
                ))
                .andExpect(status().isOk());

        jdbcTemplate.update("""
                UPDATE learning_service.spaces
                SET status = 'INACTIVE'
                WHERE id = ?
                """, seedSpaceId);

        mockMvc.perform(delete(
                        "/api/v1/admin/spaces/{space-id}",
                        seedSpaceId
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_UNMANAGED_DELETE_NOT_ALLOWED"));
    }

    @Test
    void rejectsDeletingUnmanagedInactiveSpace() throws Exception {
        Long spaceId = insertSpace(
                "통합 관리 주체 없는 삭제 차단",
                "INACTIVE",
                null
        );

        mockMvc.perform(delete("/api/v1/admin/spaces/{space-id}", spaceId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_UNMANAGED_DELETE_NOT_ALLOWED"));
    }

    @Test
    void assignedLabAllowsTypeChangeAndDeleteWhenInactive()
            throws Exception {
        Long spaceId = insertAssignedLab("통합 배정 실습실 보호");

        mockMvc.perform(put("/api/v1/admin/spaces/{space-id}", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"통합 배정 실습실 보호",
                                  "type":"STUDY",
                                  "capacity":20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("STUDY"));

        mockMvc.perform(delete("/api/v1/admin/spaces/{space-id}", spaceId))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT deleted_at IS NOT NULL
                FROM learning_service.spaces
                WHERE id = ?
                """, Boolean.class, spaceId)).isTrue();
    }

    private void assertDeactivationSucceeds(Long spaceId) throws Exception {
        assignManagementCohort(spaceId);
        mockMvc.perform(post(
                        "/api/v1/admin/spaces/{space-id}/deactivate",
                        spaceId
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":"점검"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus")
                        .value("INACTIVE"));
    }

    private void assertAvailable(Long spaceId) throws Exception {
        mockMvc.perform(get("/api/v1/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].status".formatted(spaceId)
                ).value(contains("AVAILABLE")))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupancyExpiresAt"
                                .formatted(spaceId)
                ).value(contains((Object) null)))
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)].occupiedBySameCohort"
                                .formatted(spaceId)
                ).value(contains(false)));
    }

    private Long insertSpace(
            String name,
            String operationalStatus,
            OffsetDateTime deletedAt
    ) {
        return insertTypedSpace(
                name,
                "MEETING",
                operationalStatus,
                null,
                deletedAt
        );
    }

    private void cleanupCommittedFixtures(
            List<Long> spaceIds,
            List<Long> cohortIds
    ) {
        spaceIds.forEach(spaceId -> jdbcTemplate.update(
                "DELETE FROM learning_service.spaces WHERE id = ?",
                spaceId
        ));
        cohortIds.forEach(cohortId -> {
            jdbcTemplate.update(
                    "DELETE FROM learning_service.cohort_memberships "
                            + "WHERE cohort_id = ?",
                    cohortId
            );
            jdbcTemplate.update(
                    "DELETE FROM learning_service.cohorts WHERE id = ?",
                    cohortId
            );
        });
    }

    private Long insertTypedSpace(
            String name,
            String type,
            String operationalStatus,
            Long cohortId,
            OffsetDateTime deletedAt
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.spaces (
                    name,
                    space_type,
                    capacity,
                    status,
                    cohort_id,
                    deleted_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 6, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                name,
                type,
                operationalStatus,
                cohortId,
                deletedAt,
                OFFSET_NOW,
                OFFSET_NOW
        );
    }

    private void insertOccupancy(
            Long spaceId,
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime endedAt
    ) {
        UUID userId = UUID.randomUUID();
        Long cohortId = jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name,
                    start_date,
                    end_date,
                    created_by_user_id
                ) VALUES (?, DATE '2026-01-01', DATE '2026-12-31', ?)
                RETURNING id
                """, Long.class, "점유 통합 테스트 기수 " + userId, userId);
        Long membershipId = jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id,
                    user_id,
                    role,
                    status,
                    processed_at,
                    processed_by_user_id
                ) VALUES (?, ?, 'STUDENT', 'ACTIVE', ?, ?)
                RETURNING id
                """, Long.class, cohortId, userId, OFFSET_NOW, userId);

        jdbcTemplate.update("""
                INSERT INTO learning_service.room_occupancies (
                    space_id,
                    occupier_membership_id,
                    occupier_user_id,
                    status,
                    started_at,
                    expires_at,
                    ended_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                spaceId,
                membershipId,
                userId,
                status,
                startedAt,
                expiresAt,
                endedAt
        );
    }

    private OccupancyFixture insertOccupancyWithDetails(
            Long spaceId,
            Long cohortId
    ) {
        UUID occupierUserId = UUID.randomUUID();
        UUID participantUserId = UUID.randomUUID();
        Long membershipId = insertActiveMembershipReturningId(
                cohortId,
                occupierUserId,
                "STUDENT"
        );
        Long participantMembershipId = insertActiveMembershipReturningId(
                cohortId,
                participantUserId,
                "STUDENT"
        );
        Long occupancyId = jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.room_occupancies (
                    space_id,
                    occupier_membership_id,
                    occupier_user_id,
                    status,
                    started_at,
                    expires_at
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                RETURNING id
                """,
                Long.class,
                spaceId,
                membershipId,
                occupierUserId,
                OFFSET_NOW.minusMinutes(10),
                OFFSET_NOW.plusMinutes(30)
        );
        jdbcTemplate.update("""
                INSERT INTO learning_service.occupancy_participants (
                    occupancy_id,
                    cohort_membership_id,
                    user_id,
                    joined_at
                ) VALUES (?, ?, ?, ?)
                """,
                occupancyId,
                participantMembershipId,
                participantUserId,
                OFFSET_NOW.minusMinutes(5)
        );
        return new OccupancyFixture(
                membershipId,
                occupierUserId,
                participantUserId
        );
    }

    private void assignManagementCohort(Long spaceId) {
        Long cohortId = insertManagementCohort();

        jdbcTemplate.update("""
                UPDATE learning_service.spaces
                SET cohort_id = ?
                WHERE id = ?
                """, cohortId, spaceId);
    }

    private void activateCohort(Long cohortId) {
        jdbcTemplate.update("""
                UPDATE learning_service.cohorts
                SET status = 'ACTIVE'
                WHERE id = ?
                """, cohortId);
    }

    private Long insertAssignedLab(String name) {
        Long cohortId = insertManagementCohort();

        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.spaces (
                    name,
                    space_type,
                    cohort_id,
                    capacity,
                    status,
                    created_at,
                    updated_at
                ) VALUES (?, 'LAB', ?, 20, 'INACTIVE', ?, ?)
                RETURNING id
                """, Long.class, name, cohortId, OFFSET_NOW, OFFSET_NOW);
    }

    private Long insertManagementCohort() {
        return insertCohortWithManager(MANAGER_ID);
    }

    private Long insertCohortWithManager(UUID managerId) {
        Long cohortId = jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name,
                    start_date,
                    end_date,
                    status,
                    created_by_user_id,
                    created_at,
                    updated_at,
                    version
                ) VALUES (
                    ?,
                    DATE '2026-01-01',
                    DATE '2026-12-31',
                    'PREPARING',
                    ?,
                    ?,
                    ?,
                    0
                )
                RETURNING id
                """,
                Long.class,
                "공간 관리 기수 " + UUID.randomUUID(),
                managerId,
                OFFSET_NOW,
                OFFSET_NOW
        );
        insertActiveMembership(cohortId, managerId, "MANAGER");
        return cohortId;
    }

    private Long readCohortId(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT cohort_id
                FROM learning_service.spaces
                WHERE id = ?
                """, Long.class, spaceId);
    }

    private SpaceRow readSpaceRow(Long spaceId) {
        return jdbcTemplate.queryForObject("""
                SELECT name, status, deleted_at
                FROM learning_service.spaces
                WHERE id = ?
                """,
                (resultSet, rowNumber) -> new SpaceRow(
                        resultSet.getString("name"),
                        resultSet.getString("status"),
                        resultSet.getObject(
                                "deleted_at",
                                OffsetDateTime.class
                        )
                ),
                spaceId
        );
    }

    private String assignAfterSignal(
            Long spaceId,
            Long cohortId,
            UUID actorUserId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return "START_TIMEOUT";
        }

        try {
            spaceCommandService.assignCohort(
                    spaceId,
                    cohortId,
                    actorUserId
            );
            return "SUCCESS";
        } catch (BusinessException exception) {
            return exception.getErrorCode().code();
        }
    }

    private String deleteAfterSignal(
            Long spaceId,
            UUID actorUserId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return "START_TIMEOUT";
        }

        try {
            spaceCommandService.delete(
                    spaceId,
                    actorUserId
            );
            return "DELETE_SUCCESS";
        } catch (BusinessException exception) {
            return exception.getErrorCode().code();
        }
    }

    private String activateAfterSignal(
            Long spaceId,
            UUID actorUserId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return "START_TIMEOUT";
        }

        try {
            spaceCommandService.activate(
                    spaceId,
                    actorUserId
            );
            return "ACTIVATE_SUCCESS";
        } catch (BusinessException exception) {
            return exception.getErrorCode().code();
        }
    }

    private String updateAfterSignal(
            Long spaceId,
            String name,
            UUID actorUserId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return "START_TIMEOUT";
        }

        try {
            spaceCommandService.update(
                    spaceId,
                    new UpdateSpaceCommand(
                            name,
                            SpaceType.MEETING,
                            8
                    ),
                    actorUserId
            );
            return "UPDATE_SUCCESS";
        } catch (BusinessException exception) {
            return exception.getErrorCode().code();
        }
    }

    private void insertActiveMembership(
            Long cohortId,
            UUID userId,
            String role
    ) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id,
                    user_id,
                    role,
                    status,
                    requested_at,
                    processed_at,
                    processed_by_user_id
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                cohortId,
                userId,
                role,
                OFFSET_NOW,
                OFFSET_NOW,
                userId
        );
    }

    private Long insertActiveMembershipReturningId(
            Long cohortId,
            UUID userId,
            String role
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id,
                    user_id,
                    role,
                    status,
                    requested_at,
                    processed_at,
                    processed_by_user_id
                ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                cohortId,
                userId,
                role,
                OFFSET_NOW,
                OFFSET_NOW,
                userId
        );
    }

    private record OccupancyFixture(
            Long membershipId,
            UUID occupierUserId,
            UUID participantUserId
    ) {
    }

    private record SpaceRow(
            String name,
            String status,
            OffsetDateTime deletedAt
    ) {
    }

    private String createRequest(String name, int capacity, Long cohortId) {
        return """
                {
                  "name": "%s",
                  "type": "MEETING",
                  "capacity": %d,
                  "cohortId": %d
                }
                """.formatted(name, capacity, cohortId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedIntegrationClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }
}
