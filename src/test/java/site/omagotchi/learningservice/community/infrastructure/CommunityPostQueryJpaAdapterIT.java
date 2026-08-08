package site.omagotchi.learningservice.community.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.community.domain.CommunityPostType;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({
        TestcontainersConfiguration.class,
        CommunityPostQueryJpaAdapter.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("커뮤니티 게시글 조회 저장소")
class CommunityPostQueryJpaAdapterIT {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommunityPostQueryJpaAdapter queryAdapter;

    @Test
    @DisplayName("전체 공개와 ACTIVE 소속 기수 게시글만 목록에 노출한다")
    void returnsOnlyVisiblePosts() {
        Long visibleCohortId = saveCohort("승인 기수");
        Long hiddenCohortId = saveCohort("다른 기수");
        saveActiveMembership(visibleCohortId, USER_ID);
        saveActiveMembership(hiddenCohortId, OTHER_USER_ID);
        Long globalPostId = savePost("전체 공지", "전체", CommunityPostType.NOTICE,
                CommunityPostScope.GLOBAL, null, false, "2026-08-08T00:00:00Z", null);
        Long cohortPostId = savePost("기수 공지", "기수", CommunityPostType.NOTICE,
                CommunityPostScope.COHORT, visibleCohortId, false, "2026-08-08T01:00:00Z", null);
        savePost("숨김 공지", "숨김", CommunityPostType.NOTICE,
                CommunityPostScope.COHORT, hiddenCohortId, false, "2026-08-08T02:00:00Z", null);
        savePost("삭제 공지", "삭제", CommunityPostType.NOTICE,
                CommunityPostScope.GLOBAL, null, false, "2026-08-08T03:00:00Z", "2026-08-08T04:00:00Z");

        var result = queryAdapter.findVisiblePosts(condition(USER_ID, 0, 20, null, null));

        assertAll(
                () -> assertEquals(2, result.totalElements()),
                () -> assertEquals(cohortPostId, result.items().getFirst().postId()),
                () -> assertEquals(globalPostId, result.items().getLast().postId())
        );
    }

    @Test
    @DisplayName("고정, 생성일, id 순으로 정렬하고 DB 페이지를 반환한다")
    void sortsAndPaginatesInDatabase() {
        Long firstId = savePost("첫 글", "내용", CommunityPostType.FREE,
                CommunityPostScope.GLOBAL, null, false, "2026-08-08T00:00:00Z", null);
        Long secondId = savePost("둘째 글", "내용", CommunityPostType.FREE,
                CommunityPostScope.GLOBAL, null, false, "2026-08-08T01:00:00Z", null);
        Long pinnedOldId = savePost("고정 예전 글", "내용", CommunityPostType.NOTICE,
                CommunityPostScope.GLOBAL, null, true, "2026-08-07T00:00:00Z", null);
        Long pinnedNewId = savePost("고정 최신 글", "내용", CommunityPostType.NOTICE,
                CommunityPostScope.GLOBAL, null, true, "2026-08-08T02:00:00Z", null);

        var firstPage = queryAdapter.findVisiblePosts(condition(USER_ID, 0, 2, null, null));
        var secondPage = queryAdapter.findVisiblePosts(condition(USER_ID, 1, 2, null, null));

        assertAll(
                () -> assertEquals(4, firstPage.totalElements()),
                () -> assertEquals(2, firstPage.totalPages()),
                () -> assertEquals(pinnedNewId, firstPage.items().get(0).postId()),
                () -> assertEquals(pinnedOldId, firstPage.items().get(1).postId()),
                () -> assertEquals(secondId, secondPage.items().get(0).postId()),
                () -> assertEquals(firstId, secondPage.items().get(1).postId())
        );
    }

    @Test
    @DisplayName("유형과 검색어를 DB 조건으로 적용한다")
    void filtersTypeAndSearchInDatabase() {
        Long matchedId = savePost("학습 공지", "중요 안내", CommunityPostType.NOTICE,
                CommunityPostScope.GLOBAL, null, false, "2026-08-08T00:00:00Z", null);
        savePost("학습 자유글", "중요 잡담", CommunityPostType.FREE,
                CommunityPostScope.GLOBAL, null, false, "2026-08-08T01:00:00Z", null);
        savePost("다른 공지", "무관", CommunityPostType.NOTICE,
                CommunityPostScope.GLOBAL, null, false, "2026-08-08T02:00:00Z", null);

        var result = queryAdapter.findVisiblePosts(condition(
                USER_ID,
                0,
                20,
                CommunityPostType.NOTICE,
                "학습"
        ));

        assertAll(
                () -> assertEquals(1, result.totalElements()),
                () -> assertEquals(matchedId, result.items().getFirst().postId())
        );
    }

    @Test
    @DisplayName("상세 조회도 동일한 공개 범위를 적용한다")
    void appliesVisibilityToDetail() {
        Long visibleCohortId = saveCohort("승인 기수");
        Long hiddenCohortId = saveCohort("다른 기수");
        saveActiveMembership(visibleCohortId, USER_ID);
        Long visiblePostId = savePost("보이는 글", "내용", CommunityPostType.FREE,
                CommunityPostScope.COHORT, visibleCohortId, false, "2026-08-08T00:00:00Z", null);
        Long hiddenPostId = savePost("안 보이는 글", "내용", CommunityPostType.FREE,
                CommunityPostScope.COHORT, hiddenCohortId, false, "2026-08-08T01:00:00Z", null);

        var visible = queryAdapter.findVisiblePost(USER_ID, visiblePostId);
        var hidden = queryAdapter.findVisiblePost(USER_ID, hiddenPostId);

        assertAll(
                () -> assertEquals("보이는 글", visible.orElseThrow().title()),
                () -> assertTrue(hidden.isEmpty())
        );
    }

    private CommunityPostSearchCondition condition(
            UUID userId,
            int page,
            int size,
            CommunityPostType type,
            String search
    ) {
        return new CommunityPostSearchCondition(userId, page, size, type, search);
    }

    private Long saveCohort(String name) {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.cohorts (
                            name,
                            description,
                            start_date,
                            end_date,
                            status,
                            created_by_user_id
                        )
                        values (?, '설명', '2026-08-01', '2026-08-31', 'ACTIVE', ?)
                        returning id
                        """,
                Long.class,
                name,
                AUTHOR_ID
        );
    }

    private void saveActiveMembership(Long cohortId, UUID userId) {
        jdbcTemplate.update("""
                        insert into learning_service.cohort_memberships (
                            cohort_id,
                            user_id,
                            role,
                            status,
                            requested_at,
                            processed_at,
                            processed_by_user_id
                        )
                        values (?, ?, 'STUDENT', 'ACTIVE', ?, ?, ?)
                        """,
                cohortId,
                userId,
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                AUTHOR_ID
        );
    }

    private Long savePost(
            String title,
            String content,
            CommunityPostType type,
            CommunityPostScope scope,
            Long cohortId,
            boolean pinned,
            String createdAt,
            String deletedAt
    ) {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.community_posts (
                            type,
                            title,
                            content,
                            author_user_id,
                            scope,
                            cohort_id,
                            pinned,
                            created_at,
                            updated_at,
                            deleted_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        returning id
                        """,
                Long.class,
                type.name(),
                title,
                content,
                AUTHOR_ID,
                scope.name(),
                cohortId,
                pinned,
                OffsetDateTime.parse(createdAt),
                OffsetDateTime.parse(createdAt),
                Optional.ofNullable(deletedAt)
                        .map(OffsetDateTime::parse)
                        .orElse(null)
        );
    }
}
