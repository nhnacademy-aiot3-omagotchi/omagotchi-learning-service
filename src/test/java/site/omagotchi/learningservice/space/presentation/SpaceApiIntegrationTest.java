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
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.TestcontainersConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
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
        SpaceApiIntegrationTest.FixedClockConfiguration.class
})
@Transactional
class SpaceApiIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-07-28T05:00:00Z");
    private static final OffsetDateTime OFFSET_NOW =
            OffsetDateTime.parse("2026-07-28T14:00:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                ).value(contains((Object) null)));
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
                ).value(contains(1800)));
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

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(name, 6)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.type").value("MEETING"))
                .andExpect(jsonPath("$.capacity").value(6));

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.name == '%s')].operationalStatus"
                                .formatted(name)
                ).value(contains("INACTIVE")))
                .andExpect(jsonPath(
                        "$[?(@.name == '%s')].status".formatted(name)
                ).value(contains("UNAVAILABLE")));
    }

    @Test
    void rejectsZeroCapacityWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("통합 잘못된 정원", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void rejectsDuplicateNameWithConflict() throws Exception {
        String name = "통합 중복 회의실";
        String request = createRequest(name, 6);

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_DUPLICATE_NAME"));
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

        mockMvc.perform(patch(
                        "/api/admin/spaces/{spaceId}/activate",
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

        mockMvc.perform(patch(
                        "/api/admin/spaces/{spaceId}/deactivate",
                        spaceId
                )
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

        mockMvc.perform(patch(
                        "/api/admin/spaces/{spaceId}/deactivate",
                        spaceId
                )
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

        mockMvc.perform(put("/api/admin/spaces/{spaceId}", inactiveId)
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

        mockMvc.perform(put("/api/admin/spaces/{spaceId}", activeId)
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

        mockMvc.perform(delete("/api/admin/spaces/{spaceId}", spaceId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.spaceId == %d)]".formatted(spaceId)
                ).isEmpty());

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(name, 6)))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsDeletingUnmanagedInactiveSpace() throws Exception {
        Long spaceId = insertSpace(
                "통합 관리 주체 없는 삭제 차단",
                "INACTIVE",
                null
        );

        mockMvc.perform(delete("/api/admin/spaces/{spaceId}", spaceId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPACE_UNMANAGED_DELETE_NOT_ALLOWED"));
    }

    @Test
    void returnsSpecificConflictsForAssignedLabTypeChangeAndDelete()
            throws Exception {
        Long spaceId = insertAssignedLab("통합 배정 실습실 보호");

        mockMvc.perform(put("/api/admin/spaces/{spaceId}", spaceId)
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

        mockMvc.perform(delete("/api/admin/spaces/{spaceId}", spaceId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(
                        "SPACE_ASSIGNED_LAB_DELETE_NOT_ALLOWED"
                ));
    }

    private void assertDeactivationSucceeds(Long spaceId) throws Exception {
        mockMvc.perform(patch(
                        "/api/admin/spaces/{spaceId}/deactivate",
                        spaceId
                )
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
                ).value(contains((Object) null)));
    }

    private Long insertSpace(
            String name,
            String operationalStatus,
            OffsetDateTime deletedAt
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.spaces (
                    name,
                    space_type,
                    capacity,
                    status,
                    deleted_at
                ) VALUES (?, 'MEETING', 6, ?, ?)
                RETURNING id
                """, Long.class, name, operationalStatus, deletedAt);
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
                    status
                ) VALUES (?, 'LAB', ?, 20, 'INACTIVE')
                RETURNING id
                """, Long.class, name, cohortId);
    }

    private Long insertManagementCohort() {
        UUID managerId = UUID.randomUUID();

        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name,
                    start_date,
                    end_date,
                    created_by_user_id
                ) VALUES (?, DATE '2026-01-01', DATE '2026-12-31', ?)
                RETURNING id
                """, Long.class, "공간 관리 기수 " + managerId, managerId);
    }

    private String createRequest(String name, int capacity) {
        return """
                {
                  "name": "%s",
                  "type": "MEETING",
                  "capacity": %d
                }
                """.formatted(name, capacity);
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
