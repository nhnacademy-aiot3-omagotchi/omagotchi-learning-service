package site.omagotchi.learningservice.community.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.community.domain.CommunityPost;

import java.util.Optional;

public interface CommunityPostJpaRepository extends JpaRepository<CommunityPost, Long> {

    Optional<CommunityPost> findByIdAndDeletedAtIsNull(Long id);
}
