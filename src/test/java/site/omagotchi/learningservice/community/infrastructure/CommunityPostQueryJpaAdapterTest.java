package site.omagotchi.learningservice.community.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.community.application.query.CommunityPostListItem;
import site.omagotchi.learningservice.community.application.query.CommunityPostSearchCondition;
import site.omagotchi.learningservice.community.domain.CommunityPostType;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("커뮤니티 게시글 조회 JPA 어댑터")
class CommunityPostQueryJpaAdapterTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    @DisplayName("목록 조회 JPQL에 공개 범위, 검색, 유형, 정렬, 페이지 조건을 적용한다")
    @SuppressWarnings("unchecked")
    void appliesVisibilitySearchTypeOrderingAndPaginationToQuery() {
        EntityManager entityManager = mock(EntityManager.class);
        TypedQuery<CommunityPostListItem> listQuery = mock(TypedQuery.class);
        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        StringBuilder listJpql = new StringBuilder();
        StringBuilder countJpql = new StringBuilder();
        when(entityManager.createQuery(anyString(), eq(CommunityPostListItem.class)))
                .thenAnswer(invocation -> {
                    listJpql.append(invocation.getArgument(0, String.class));
                    return listQuery;
                });
        when(entityManager.createQuery(anyString(), eq(Long.class)))
                .thenAnswer(invocation -> {
                    countJpql.append(invocation.getArgument(0, String.class));
                    return countQuery;
                });
        stubQuery(listQuery);
        stubQuery(countQuery);
        when(listQuery.getResultList()).thenReturn(List.of());
        when(countQuery.getSingleResult()).thenReturn(0L);
        CommunityPostQueryJpaAdapter adapter = new CommunityPostQueryJpaAdapter(entityManager);

        adapter.findVisiblePosts(new CommunityPostSearchCondition(
                USER_ID,
                2,
                10,
                CommunityPostType.NOTICE,
                "학습"
        ));

        assertAll(
                () -> assertTrue(listJpql.toString().contains("exists (")),
                () -> assertTrue(listJpql.toString().contains("membership.userId = :userId")),
                () -> assertTrue(listJpql.toString().contains("membership.status = :activeStatus")),
                () -> assertTrue(listJpql.toString().contains("post.type = :type")),
                () -> assertTrue(listJpql.toString().contains("lower(post.title) like :search")),
                () -> assertTrue(listJpql.toString().contains("post.pinned desc")),
                () -> assertTrue(listJpql.toString().contains("post.createdAt desc")),
                () -> assertTrue(listJpql.toString().contains("post.id desc")),
                () -> assertTrue(countJpql.toString().contains("count(post.id)"))
        );
        verify(listQuery).setFirstResult(20);
        verify(listQuery).setMaxResults(10);
        verify(listQuery).setParameter("type", CommunityPostType.NOTICE);
        verify(listQuery).setParameter("search", "%학습%");
        verify(countQuery).setParameter("type", CommunityPostType.NOTICE);
        verify(countQuery).setParameter("search", "%학습%");
        verify(listQuery).setParameter("activeStatus", CohortMembershipStatus.ACTIVE);
    }

    private void stubQuery(Query query) {
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        when(query.setFirstResult(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(query);
        when(query.setMaxResults(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(query);
        when(query.setHint(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
    }
}
