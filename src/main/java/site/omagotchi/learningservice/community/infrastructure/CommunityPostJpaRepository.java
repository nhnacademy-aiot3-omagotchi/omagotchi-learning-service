package site.omagotchi.learningservice.community.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.community.domain.CommunityPost;

import java.util.Optional;

public interface CommunityPostJpaRepository extends JpaRepository<CommunityPost, Long> {

    Optional<CommunityPost> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 기수 게시판 안에서의 조회. 다른 기수 게시글 식별자를 넣으면 비어 있는 결과가 나오고,
     * 호출부가 이를 404로 옮겨 기수 경계 밖 게시글의 존재를 숨긴다.
     */
    Optional<CommunityPost> findByIdAndCohortIdAndDeletedAtIsNull(Long id, Long cohortId);
}
