package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.gamification.application.port.LevelPolicyQueryRepository;
import site.omagotchi.learningservice.gamification.domain.LevelPolicy;

import java.util.List;

/** 레벨 정책 조회의 Spring Data JPA 구현. */
@Repository
@RequiredArgsConstructor
public class LevelPolicyJpaPersistence implements LevelPolicyQueryRepository {

    private final LevelPolicyRepository levelPolicyRepository;

    @Override
    public List<LevelPolicy> findUpToLevel(int level) {
        return levelPolicyRepository.findByLevelLessThanEqualOrderByLevelAsc(level);
    }
}
