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
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.global.config.QueryDslConfig;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        CommunityPostQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("커뮤니티 게시글 조회 저장소")
class CommunityPostQueryDslRepositoryIT {

    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommunityPostQueryDslRepository queryRepository;

    @Test
    @DisplayName("조회한 기수의 게시글만 목록에 노출한다")
    void returnsOnlyPostsOfRequestedCohort() {
        Long cohortId = saveCohort("승인 기수");
        Long otherCohortId = saveCohort("다른 기수");
        Long noticeId = savePost("기수 공지", "기수", CommunityPostType.NOTICE,
                cohortId, false, "2026-08-08T01:00:00Z", null);
        savePost("다른 기수 공지", "숨김", CommunityPostType.NOTICE,
                otherCohortId, false, "2026-08-08T02:00:00Z", null);
        savePost("삭제된 공지", "삭제", CommunityPostType.NOTICE,
                cohortId, false, "2026-08-08T03:00:00Z", "2026-08-08T04:00:00Z");

        var result = queryRepository.findVisiblePosts(condition(cohortId, 0, 20, null, null));

        assertAll(
                () -> assertEquals(1, result.totalElements()),
                () -> assertEquals(noticeId, result.items().getFirst().postId())
        );
    }

    @Test
    @DisplayName("생성일, id 역순으로 정렬하고 DB 페이지를 반환한다")
    void sortsAndPaginatesInDatabase() {
        Long cohortId = saveCohort("기수");
        Long firstId = savePost("첫 글", "내용", CommunityPostType.FREE,
                cohortId, false, "2026-08-08T00:00:00Z", null);
        Long secondId = savePost("둘째 글", "내용", CommunityPostType.FREE,
                cohortId, false, "2026-08-08T01:00:00Z", null);
        Long thirdId = savePost("셋째 글", "내용", CommunityPostType.FREE,
                cohortId, false, "2026-08-08T02:00:00Z", null);
        Long fourthId = savePost("넷째 글", "내용", CommunityPostType.FREE,
                cohortId, false, "2026-08-08T03:00:00Z", null);

        var firstPage = queryRepository.findVisiblePosts(condition(cohortId, 0, 2, null, null));
        var secondPage = queryRepository.findVisiblePosts(condition(cohortId, 1, 2, null, null));

        assertAll(
                () -> assertEquals(4, firstPage.totalElements()),
                () -> assertEquals(2, firstPage.totalPages()),
                () -> assertEquals(fourthId, firstPage.items().get(0).postId()),
                () -> assertEquals(thirdId, firstPage.items().get(1).postId()),
                () -> assertEquals(secondId, secondPage.items().get(0).postId()),
                () -> assertEquals(firstId, secondPage.items().get(1).postId())
        );
    }

    @Test
    @DisplayName("고정 공지는 목록에서 빼고 따로 조회한다")
    void keepsPinnedPostOutOfList() {
        Long cohortId = saveCohort("기수");
        Long pinnedId = savePost("고정 공지", "내용", CommunityPostType.NOTICE,
                cohortId, true, "2026-08-07T00:00:00Z", null);
        Long normalId = savePost("일반 글", "내용", CommunityPostType.FREE,
                cohortId, false, "2026-08-08T00:00:00Z", null);

        var list = queryRepository.findVisiblePosts(condition(cohortId, 0, 20, null, null));
        var pinned = queryRepository.findPinnedPost(cohortId);

        assertAll(
                () -> assertEquals(1, list.totalElements()),
                () -> assertEquals(normalId, list.items().getFirst().postId()),
                () -> assertEquals(pinnedId, pinned.orElseThrow().postId()),
                () -> assertTrue(pinned.orElseThrow().pinned())
        );
    }

    @Test
    @DisplayName("고정 공지 조회도 기수 경계를 지키고, 없으면 비어 있다")
    void findsPinnedPostWithinCohortOnly() {
        Long cohortId = saveCohort("기수");
        Long otherCohortId = saveCohort("다른 기수");
        savePost("다른 기수 고정", "내용", CommunityPostType.NOTICE,
                otherCohortId, true, "2026-08-07T00:00:00Z", null);

        assertTrue(queryRepository.findPinnedPost(cohortId).isEmpty());
    }

    @Test
    @DisplayName("유형과 검색어를 DB 조건으로 적용한다")
    void filtersTypeAndSearchInDatabase() {
        Long cohortId = saveCohort("기수");
        Long matchedId = savePost("학습 공지", "중요 안내", CommunityPostType.NOTICE,
                cohortId, false, "2026-08-08T00:00:00Z", null);
        savePost("학습 자유글", "중요 잡담", CommunityPostType.FREE,
                cohortId, false, "2026-08-08T01:00:00Z", null);
        savePost("다른 공지", "무관", CommunityPostType.NOTICE,
                cohortId, false, "2026-08-08T02:00:00Z", null);

        var result = queryRepository.findVisiblePosts(condition(
                cohortId,
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
    @DisplayName("검색어의 LIKE 와일드카드는 문자 그대로 취급한다")
    void escapesLikeWildcardsInSearch() {
        Long cohortId = saveCohort("기수");
        Long literalId = savePost("할인 50% 안내", "내용", CommunityPostType.NOTICE,
                cohortId, false, "2026-08-08T00:00:00Z", null);
        savePost("무관한 공지", "내용", CommunityPostType.NOTICE,
                cohortId, false, "2026-08-08T01:00:00Z", null);

        var result = queryRepository.findVisiblePosts(condition(cohortId, 0, 20, null, "50%"));

        assertAll(
                () -> assertEquals(1, result.totalElements()),
                () -> assertEquals(literalId, result.items().getFirst().postId())
        );
    }

    @Test
    @DisplayName("상세 조회도 기수 경계를 적용한다")
    void appliesCohortBoundaryToDetail() {
        Long cohortId = saveCohort("승인 기수");
        Long otherCohortId = saveCohort("다른 기수");
        Long visiblePostId = savePost("보이는 글", "내용", CommunityPostType.FREE,
                cohortId, false, "2026-08-08T00:00:00Z", null);
        Long otherCohortPostId = savePost("안 보이는 글", "내용", CommunityPostType.FREE,
                otherCohortId, false, "2026-08-08T01:00:00Z", null);

        var visible = queryRepository.findVisiblePost(cohortId, visiblePostId);
        var hidden = queryRepository.findVisiblePost(cohortId, otherCohortPostId);

        assertAll(
                () -> assertEquals("보이는 글", visible.orElseThrow().title()),
                () -> assertTrue(hidden.isEmpty())
        );
    }

    private CommunityPostSearchCondition condition(
            Long cohortId,
            int page,
            int size,
            CommunityPostType type,
            String search
    ) {
        return new CommunityPostSearchCondition(cohortId, page, size, type, search);
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

    private Long savePost(
            String title,
            String content,
            CommunityPostType type,
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
                            cohort_id,
                            pinned,
                            created_at,
                            updated_at,
                            deleted_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        returning id
                        """,
                Long.class,
                type.name(),
                title,
                content,
                AUTHOR_ID,
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
