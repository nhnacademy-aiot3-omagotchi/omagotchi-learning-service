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
import site.omagotchi.learningservice.global.auth.GlobalRole;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
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

        mockMvc.perform(get("/api/spaces"))
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

        mockMvc.perform(get("/api/spaces"))
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

        mockMvc.perform(get("/api/spaces"))
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
        Long spaceId = insertSpace(
                "통합 동일 기수 점유 회의실",
                "ACTIVE",
                null
        );
        OccupancyFixture occupancy = insertOccupancyWithDetails(
                spaceId,
                cohortId
        );

        mockMvc.perform(get("/api/spaces")
                        .header("X-User-Id", requesterUserId))
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
        insertCohortWithManager(requesterUserId);
        Long occupierCohortId = insertCohortWithManager(UUID.randomUUID());
        Long spaceId = insertSpace(
                "통합 타 기수 점유 회의실",
                "ACTIVE",
                null
        );
        insertOccupancyWithDetails(spaceId, occupierCohortId);

        mockMvc.perform(get("/api/spaces")
                        .header("X-User-Id", requesterUserId))
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

        mockMvc.perform(get("/api/spaces"))
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

        mockMvc.perform(get("/api/spaces"))
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

        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(name, 6, cohortId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.type").value("MEETING"))
                .andExpect(jsonPath("$.capacity").value(6))
                .andExpect(jsonPath("$.cohortId").value(cohortId));

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.name == '%s')].operationalStatus"
                                .formatted(name)
                ).value(contains("INACTIVE")))
                .andExpect(jsonPath(
                        "$[?(@.name == '%s')].status".formatted(name)
                ).value(contains("UNAVAILABLE")));

        mockMvc.perform(get("/api/spaces"))
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

        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", MANAGER_ID)
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

        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", MANAGER_ID)
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

        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", MANAGER_ID)
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
        Long firstCohortId = insertCohortWithManager(UUID.randomUUID());
        Long secondCohortId = insertCohortWithManager(UUID.randomUUID());
        Long labId = insertTypedSpace(
                "통합 동시 배정 실습실 " + UUID.randomUUID(),
                "LAB",
                "INACTIVE",
                null,
                null
        );
        UUID systemAdminId = UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> first = executor.submit(() -> assignAfterSignal(
                    labId,
                    firstCohortId,
                    systemAdminId,
                    ready,
                    start
            ));
            Future<String> second = executor.submit(() -> assignAfterSignal(
                    labId,
                    secondCohortId,
                    systemAdminId,
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
                            "SPACE_LAB_ALREADY_ASSIGNED"
                    );
            assertThat(readCohortId(labId))
                    .isIn(firstCohortId, secondCohortId);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deleteAndActivateDoNotCreateDeletedActiveSpace()
            throws Exception {
        Long cohortId = insertCohortWithManager(UUID.randomUUID());
        Long spaceId = insertTypedSpace(
                "통합 삭제 활성화 경쟁 " + UUID.randomUUID(),
                "MEETING",
                "INACTIVE",
                cohortId,
                null
        );
        UUID systemAdminId = UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> deletion = executor.submit(() ->
                    deleteAfterSignal(
                            spaceId,
                            systemAdminId,
                            ready,
                            start
                    ));
            Future<String> activation = executor.submit(() ->
                    activateAfterSignal(
                            spaceId,
                            systemAdminId,
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
                        .isEqualTo("SPACE_ALREADY_DELETED");
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
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deleteAndUpdateDoNotReviveDeletedSpace()
            throws Exception {
        Long cohortId = insertCohortWithManager(UUID.randomUUID());
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
        UUID systemAdminId = UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> deletion = executor.submit(() ->
                    deleteAfterSignal(
                            spaceId,
                            systemAdminId,
                            ready,
                            start
                    ));
            Future<String> update = executor.submit(() ->
                    updateAfterSignal(
                            spaceId,
                            updatedName,
                            systemAdminId,
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
                    .isIn("UPDATE_SUCCESS", "SPACE_ALREADY_DELETED");
            assertThat(finalState.deletedAt()).isNotNull();

            if ("UPDATE_SUCCESS".equals(updateResult)) {
                assertThat(finalState.name()).isEqualTo(updatedName);
            } else {
                assertThat(finalState.name()).isEqualTo(originalName);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsZeroCapacityWithBadRequest() throws Exception {
        Long cohortId = insertManagementCohort();
        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("통합 잘못된 정원", 0, cohortId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void rejectsDuplicateNameWithConflict() throws Exception {
        String name = "통합 중복 회의실";
        Long cohortId = insertManagementCohort();
        String request = createRequest(name, 6, cohortId);

        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code")
                        .value("SPACE_DUPLICATE_NAME"));
    }

    @Test
    void rejectsSpaceCreationByActiveNonManager() throws Exception {
        Long cohortId = insertManagementCohort();
        UUID studentId = UUID.randomUUID();
        insertActiveMembership(cohortId, studentId, "STUDENT");

        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", studentId)
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

        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", UUID.randomUUID())
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

        mockMvc.perform(patch(
                        "/api/admin/spaces/{space-id}/activate",
                        spaceId
                ).header("X-User-Id", MANAGER_ID))
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

        mockMvc.perform(patch(
                        "/api/admin/spaces/{space-id}/deactivate",
                        spaceId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
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

        mockMvc.perform(patch(
                        "/api/admin/spaces/{space-id}/deactivate",
                        spaceId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
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

        mockMvc.perform(put("/api/admin/spaces/{space-id}", inactiveId)
                        .header("X-User-Id", MANAGER_ID)
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

        mockMvc.perform(put("/api/admin/spaces/{space-id}", activeId)
                        .header("X-User-Id", MANAGER_ID)
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

        mockMvc.perform(delete("/api/admin/spaces/{space-id}", spaceId)
                        .header("X-User-Id", MANAGER_ID))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)]".formatted(spaceId)
                ).isEmpty());

        Long cohortId = insertManagementCohort();
        mockMvc.perform(post("/api/admin/spaces")
                        .header("X-User-Id", MANAGER_ID)
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
                        "/api/admin/spaces/{space-id}",
                        activeSpaceId
                ).header("X-User-Id", MANAGER_ID))
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
                        "/api/admin/spaces/{space-id}",
                        otherSpaceId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
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

        mockMvc.perform(patch(
                        "/api/admin/spaces/{space-id}/activate",
                        otherSpaceId
                ).header("X-User-Id", MANAGER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));

        mockMvc.perform(patch(
                        "/api/admin/spaces/{space-id}/deactivate",
                        otherSpaceId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":"점검"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));

        mockMvc.perform(delete(
                        "/api/admin/spaces/{space-id}",
                        otherSpaceId
                ).header("X-User-Id", MANAGER_ID))
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
                        "/api/admin/spaces/{space-id}",
                        spaceId
                ).header("X-User-Id", MANAGER_ID))
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
                        "/api/admin/spaces/{space-id}/cohort",
                        labId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cohortId":%d}
                                """.formatted(cohortId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").value(cohortId))
                .andExpect(jsonPath("$.updatedAt")
                        .value("2026-07-28T14:00:00+09:00"));

        assertThat(readCohortId(labId)).isEqualTo(cohortId);

        mockMvc.perform(delete(
                        "/api/admin/spaces/{space-id}/cohort",
                        labId
                ).header("X-User-Id", MANAGER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortId").isEmpty());

        assertThat(readCohortId(labId)).isNull();
    }

    @Test
    void rejectsInvalidLabAssignmentStates() throws Exception {
        Long cohortId = insertManagementCohort();
        Long assignedLabId = insertTypedSpace(
                "통합 중복 배정 실습실",
                "LAB",
                "INACTIVE",
                cohortId,
                null
        );
        Long meetingId = insertTypedSpace(
                "통합 회의실 배정 차단",
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
                        "/api/admin/spaces/{space-id}/cohort",
                        assignedLabId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_LAB_ALREADY_ASSIGNED"));

        mockMvc.perform(put(
                        "/api/admin/spaces/{space-id}/cohort",
                        meetingId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_LAB_ONLY_COHORT_ASSIGNMENT"));

        mockMvc.perform(put(
                        "/api/admin/spaces/{space-id}/cohort",
                        deletedLabId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ALREADY_DELETED"));

        mockMvc.perform(put(
                        "/api/admin/spaces/{space-id}/cohort",
                        Long.MAX_VALUE
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));

        mockMvc.perform(put(
                        "/api/admin/spaces/{space-id}/cohort",
                        assignedLabId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cohortId":9223372036854775807}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COHORT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidLabUnassignmentStates() throws Exception {
        Long cohortId = insertManagementCohort();
        Long meetingId = insertTypedSpace(
                "통합 회의실 해제 차단",
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

        mockMvc.perform(delete(
                        "/api/admin/spaces/{space-id}/cohort",
                        meetingId
                ).header("X-User-Id", MANAGER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_LAB_ONLY_COHORT_ASSIGNMENT"));

        mockMvc.perform(delete(
                        "/api/admin/spaces/{space-id}/cohort",
                        unassignedLabId
                ).header("X-User-Id", UUID.randomUUID())
                        .header("X-Global-Role", "SYSTEM_ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_LAB_NOT_ASSIGNED"));
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

        mockMvc.perform(put(
                        "/api/admin/spaces/{space-id}/cohort",
                        labId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cohortId":%d}
                                """.formatted(ownCohortId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));

        mockMvc.perform(delete(
                        "/api/admin/spaces/{space-id}/cohort",
                        labId
                ).header("X-User-Id", MANAGER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_ACCESS_DENIED"));
    }

    @Test
    void systemAdminCanManageSeedSpaceButCannotDeleteIt()
            throws Exception {
        Long seedSpaceId = insertSpace(
                "통합 시스템 관리자 시드 공간",
                "INACTIVE",
                null
        );

        mockMvc.perform(patch(
                        "/api/admin/spaces/{space-id}/activate",
                        seedSpaceId
                ).header("X-User-Id", UUID.randomUUID())
                        .header("X-Global-Role", "SYSTEM_ADMIN"))
                .andExpect(status().isOk());

        jdbcTemplate.update("""
                UPDATE learning_service.spaces
                SET status = 'INACTIVE'
                WHERE id = ?
                """, seedSpaceId);

        mockMvc.perform(delete(
                        "/api/admin/spaces/{space-id}",
                        seedSpaceId
                ).header("X-User-Id", UUID.randomUUID())
                        .header("X-Global-Role", "SYSTEM_ADMIN"))
                .andExpect(status().isConflict())
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

        mockMvc.perform(delete("/api/admin/spaces/{space-id}", spaceId)
                        .header("X-User-Id", MANAGER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_UNMANAGED_DELETE_NOT_ALLOWED"));
    }

    @Test
    void assignedLabRejectsTypeChangeButCanBeDeletedWhenInactive()
            throws Exception {
        Long spaceId = insertAssignedLab("통합 배정 실습실 보호");

        mockMvc.perform(put("/api/admin/spaces/{space-id}", spaceId)
                        .header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"통합 배정 실습실 보호",
                                  "type":"STUDY",
                                  "capacity":20
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(
                        "SPACE_ASSIGNED_LAB_TYPE_CHANGE_NOT_ALLOWED"
                ));

        mockMvc.perform(delete("/api/admin/spaces/{space-id}", spaceId)
                        .header("X-User-Id", MANAGER_ID))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT deleted_at IS NOT NULL
                FROM learning_service.spaces
                WHERE id = ?
                """, Boolean.class, spaceId)).isTrue();
    }

    private void assertDeactivationSucceeds(Long spaceId) throws Exception {
        assignManagementCohort(spaceId);
        mockMvc.perform(patch(
                        "/api/admin/spaces/{space-id}/deactivate",
                        spaceId
                ).header("X-User-Id", MANAGER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inactiveReason":"점검"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus")
                        .value("INACTIVE"));
    }

    private void assertAvailable(Long spaceId) throws Exception {
        mockMvc.perform(get("/api/spaces"))
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
                    actorUserId,
                    GlobalRole.SYSTEM_ADMIN
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
                    actorUserId,
                    GlobalRole.SYSTEM_ADMIN
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
                    actorUserId,
                    GlobalRole.SYSTEM_ADMIN
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
                    actorUserId,
                    GlobalRole.SYSTEM_ADMIN
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
