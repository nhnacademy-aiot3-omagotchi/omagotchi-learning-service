package site.omagotchi.learningservice.gamification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.gamification.domain.LevelPolicy;

import java.util.List;

/**
 * 레벨 정책
 */
public interface LevelPolicyRepository extends JpaRepository<LevelPolicy, Integer> {

    List<LevelPolicy> findByLevelLessThanEqualOrderByLevelAsc(int level);

    List<LevelPolicy> findAllByOrderByLevelAsc();
}
