package site.omagotchi.learningservice.community.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.community.domain.CommunityPost;

public interface CommunityPostJpaRepository extends JpaRepository<CommunityPost, Long> {
}
