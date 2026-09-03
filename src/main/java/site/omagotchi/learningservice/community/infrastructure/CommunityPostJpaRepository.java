package site.omagotchi.learningservice.community.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.community.domain.CommunityPost;

import java.util.Optional;

public interface CommunityPostJpaRepository extends JpaRepository<CommunityPost, Long> {

    Optional<CommunityPost> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 기수 게시판 안에서의 조회. 다른 기수 게시글 식별자를 넣으면 비어 있는 결과가 나오고,
     * 호출부가 이를 404로 옮겨 기수 경계 밖 게시글의 존재를 숨긴다.
     */
    Optional<CommunityPost> findByIdAndCohortIdAndDeletedAtIsNull(Long id, Long cohortId);

    /**
     * 기수의 고정 공지는 하나다. 새로 고정하기 전에 기존 고정을 모두 내린다.
     *
     * @return 이번 호출로 고정이 내려간 게시글 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CommunityPost post
               set post.pinned = false
             where post.cohortId = :cohortId
               and post.pinned = true
               and post.deletedAt is null
            """)
    int unpinAll(@Param("cohortId") Long cohortId);
}
