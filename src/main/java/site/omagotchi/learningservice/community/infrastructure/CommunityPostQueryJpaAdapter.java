package site.omagotchi.learningservice.community.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.community.application.port.CommunityPostQueryPort;
import site.omagotchi.learningservice.community.application.query.CommunityPostDetail;
import site.omagotchi.learningservice.community.application.query.CommunityAttachmentMetadata;
import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.application.query.CommunityPostPage;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityPost;
import site.omagotchi.learningservice.community.domain.CommunityPostScope;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 커뮤니티 목록/상세 조회를 DB 쿼리로 수행하는 JPA 어댑터다.
 *
 * <p>GLOBAL/COHORT 가시성, 검색, 타입 필터, 정렬, 페이징을 메모리 필터링 없이 SQL 조건으로 처리한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class CommunityPostQueryJpaAdapter implements CommunityPostQueryPort {

    // COHORT 게시글은 ACTIVE membership 존재 여부를 EXISTS로 확인해 N+1과 사후 필터링을 피한다.
    private static final String VISIBLE_CONDITION = """
            post.deletedAt is null
            and (
                post.scope = :globalScope
                or (
                    post.scope = :cohortScope
                    and exists (
                        select 1
                        from CohortMembership membership
                        where membership.userId = :userId
                          and membership.cohortId = post.cohortId
                          and membership.status = :activeStatus
                          and membership.endedAt is null
                    )
                )
            )
            """;

    private final EntityManager entityManager;
    private final CommunityPostAttachmentRepository attachmentRepository;

    @Override
    public CommunityPostPage findVisiblePosts(CommunityPostSearchCondition condition) {
        String filters = filters(condition);

        TypedQuery<CommunityPostListItem> listQuery = listQuery(filters);
        bindCommonParameters(listQuery, condition.userId());
        bindOptionalParameters(listQuery, condition);
        List<CommunityPostListItem> items = listQuery
                .setFirstResult(condition.page() * condition.size())
                .setMaxResults(condition.size())
                .setHint("org.hibernate.readOnly", true)
                .getResultList();

        TypedQuery<Long> countQuery = countQuery(filters);
        bindCommonParameters(countQuery, condition.userId());
        bindOptionalParameters(countQuery, condition);
        long totalElements = countQuery.getSingleResult();

        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / condition.size());
        return new CommunityPostPage(
                items,
                condition.page(),
                condition.size(),
                totalElements,
                totalPages
        );
    }

    @Override
    public Optional<CommunityPostDetail> findVisiblePost(UUID userId, Long postId) {
        List<CommunityPost> result = entityManager.createQuery("""
                        select post
                        from CommunityPost post
                        where post.id = :postId
                          and
                        """ + VISIBLE_CONDITION,
                        CommunityPost.class
                )
                .setParameter("postId", postId)
                .setParameter("userId", userId)
                .setParameter("globalScope", CommunityPostScope.GLOBAL)
                .setParameter("cohortScope", CommunityPostScope.COHORT)
                .setParameter("activeStatus", CohortMembershipStatus.ACTIVE)
                .setHint("org.hibernate.readOnly", true)
                .getResultList();
        return result.stream()
                .findFirst()
                .map(post -> CommunityPostDetail.from(post, findAttachments(post.getId())));
    }

    private TypedQuery<CommunityPostListItem> listQuery(String filters) {
        return entityManager.createQuery("""
                        select new site.omagotchi.learningservice.community.application.query.CommunityPostListItem(
                            post.id,
                            post.type,
                            post.title,
                            post.authorUserId,
                            post.scope,
                            post.cohortId,
                            post.pinned,
                            post.createdAt,
                            post.updatedAt,
                            (
                                select count(attachment.id)
                                from CommunityPostAttachment attachment
                                where attachment.postId = post.id
                            )
                        )
                        from CommunityPost post
                        where
                        """ + VISIBLE_CONDITION + filters + """

                        order by post.pinned desc,
                                 post.createdAt desc,
                                 post.id desc
                        """,
                CommunityPostListItem.class);
    }

    private TypedQuery<Long> countQuery(String filters) {
        return entityManager.createQuery("""
                        select count(post.id)
                        from CommunityPost post
                        where
                        """ + VISIBLE_CONDITION + filters,
                Long.class);
    }

    private String filters(CommunityPostSearchCondition condition) {
        StringBuilder filters = new StringBuilder();
        if (condition.type() != null) {
            filters.append("\n  and post.type = :type");
        }
        if (condition.search() != null) {
            filters.append("""

                      and (
                          lower(post.title) like :search
                          or lower(post.content) like :search
                      )""");
        }
        return filters.toString();
    }

    private void bindCommonParameters(Query query, UUID userId) {
        query.setParameter("userId", userId)
                .setParameter("globalScope", CommunityPostScope.GLOBAL)
                .setParameter("cohortScope", CommunityPostScope.COHORT)
                .setParameter("activeStatus", CohortMembershipStatus.ACTIVE);
    }

    private void bindOptionalParameters(Query query, CommunityPostSearchCondition condition) {
        if (condition.type() != null) {
            query.setParameter("type", condition.type());
        }
        if (condition.search() != null) {
            query.setParameter("search", "%" + condition.search().toLowerCase() + "%");
        }
    }

    private List<CommunityAttachmentMetadata> findAttachments(Long postId) {
        return attachmentRepository.findByPostIdOrderByDisplayOrderAscIdAsc(postId)
                .stream()
                .map(CommunityAttachmentMetadata::from)
                .toList();
    }
}
