package site.omagotchi.learningservice.community.infrastructure;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.community.application.port.CommunityPostQueryPort;
import site.omagotchi.learningservice.community.application.query.CommunityAttachmentMetadata;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.community.domain.QCommunityPost;
import site.omagotchi.learningservice.community.domain.QCommunityPostAttachment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 커뮤니티 게시글 목록/상세 조회를 담당한다.
 *
 * <p>게시판은 기수 단위다. 조회 진입에서 이미 해당 기수의 ACTIVE 소속을 검증하므로
 * 여기에서는 membership을 다시 확인하지 않고 cohort_id로만 가른다.</p>
 *
 * <p>상세 조회는 조건이 고정이라 쿼리 메서드로 충분하고, 유형·검색어가 선택인 목록만
 * QueryDSL로 조립한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class CommunityPostQueryDslRepository implements CommunityPostQueryPort {

    private static final QCommunityPost post = QCommunityPost.communityPost;
    private static final QCommunityPostAttachment attachment =
            QCommunityPostAttachment.communityPostAttachment;

    // LIKE 와일드카드를 문자 그대로 다루기 위한 escape 문자.
    private static final char LIKE_ESCAPE = '!';

    private final JPAQueryFactory queryFactory;
    private final CommunityPostJpaRepository postRepository;
    private final CommunityPostAttachmentRepository attachmentRepository;

    /*
     * SELECT p.id, p.type, p.title, p.author_user_id, p.cohort_id, p.pinned,
     *        p.created_at, p.updated_at,
     *        (SELECT count(*) FROM community_post_attachments a WHERE a.post_id = p.id)
     * FROM learning_service.community_posts p
     * WHERE p.deleted_at IS NULL
     *   AND p.cohort_id = :cohortId
     *   AND NOT p.pinned                                      -- 고정 공지는 배너로 빠진다
     *   AND p.type = :type                                    -- type이 null이 아닐 때만
     *   AND (lower(p.title) LIKE :search ESCAPE '!'
     *        OR lower(p.content) LIKE :search ESCAPE '!')     -- search가 null이 아닐 때만
     * ORDER BY p.created_at DESC, p.id DESC
     * LIMIT :size OFFSET :page * :size;
     *
     * ix_community_posts_cohort_list와 컬럼 순서가 같아 정렬까지 인덱스로 처리된다.
     */
    @Override
    public CommunityPostPage findVisiblePosts(CommunityPostSearchCondition condition) {
        BooleanExpression[] predicates = {
                activeInCohort(condition.cohortId()),
                post.pinned.isFalse(),
                typeEquals(condition.type()),
                searchMatches(condition.search())
        };

        List<CommunityPostListItem> items = queryFactory
                .select(listItemProjection())
                .from(post)
                .where(predicates)
                .orderBy(post.createdAt.desc(), post.id.desc())
                .offset((long) condition.page() * condition.size())
                .limit(condition.size())
                .fetch();

        long totalElements = Optional.ofNullable(
                queryFactory.select(post.count())
                        .from(post)
                        .where(predicates)
                        .fetchOne()
        ).orElse(0L);

        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / condition.size());
        return new CommunityPostPage(
                items,
                null,
                condition.page(),
                condition.size(),
                totalElements,
                totalPages
        );
    }

    /*
     * SELECT ... FROM community_posts p
     * WHERE p.deleted_at IS NULL AND p.cohort_id = :cohortId AND p.pinned
     * ORDER BY p.created_at DESC, p.id DESC
     * LIMIT 1;
     */
    @Override
    public Optional<CommunityPostListItem> findPinnedPost(Long cohortId) {
        // 기수의 고정 공지는 하나지만, 혹시 둘이 되어도 화면이 흔들리지 않게 최신 하나만 집는다.
        return Optional.ofNullable(queryFactory
                .select(listItemProjection())
                .from(post)
                .where(activeInCohort(cohortId), post.pinned.isTrue())
                .orderBy(post.createdAt.desc(), post.id.desc())
                .fetchFirst());
    }

    @Override
    public Optional<CommunityPostDetail> findVisiblePost(Long cohortId, Long postId) {
        return postRepository.findByIdAndCohortIdAndDeletedAtIsNull(postId, cohortId)
                .map(found -> CommunityPostDetail.from(found, findAttachments(found.getId())));
    }

    private ConstructorExpression<CommunityPostListItem> listItemProjection() {
        return Projections.constructor(
                CommunityPostListItem.class,
                post.id,
                post.type,
                post.title,
                post.authorUserId,
                post.cohortId,
                post.pinned,
                post.createdAt,
                post.updatedAt,
                JPAExpressions.select(attachment.id.count())
                        .from(attachment)
                        .where(attachment.postId.eq(post.id))
        );
    }

    private BooleanExpression activeInCohort(Long cohortId) {
        return post.deletedAt.isNull().and(post.cohortId.eq(cohortId));
    }

    /**
     * null을 반환하면 QueryDSL이 조건에서 제외한다. 유형 필터가 없는 "전체" 탭이 그 경우다.
     */
    private BooleanExpression typeEquals(CommunityPostType type) {
        return type == null ? null : post.type.eq(type);
    }

    /**
     * 검색어의 LIKE 와일드카드를 이스케이프한다.
     * 그대로 두면 "50%" 검색이 전체 일치가 된다.
     */
    private BooleanExpression searchMatches(String search) {
        if (search == null) {
            return null;
        }
        String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";
        return post.title.lower().like(pattern, LIKE_ESCAPE)
                .or(post.content.lower().like(pattern, LIKE_ESCAPE));
    }

    private String escapeLike(String search) {
        return search.replace(String.valueOf(LIKE_ESCAPE), "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private List<CommunityAttachmentMetadata> findAttachments(Long postId) {
        return attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(postId)
                .stream()
                .map(CommunityAttachmentMetadata::from)
                .toList();
    }
}
