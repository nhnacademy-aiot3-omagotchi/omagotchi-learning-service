package site.omagotchi.learningservice.gamification.application.port;

import site.omagotchi.learningservice.gamification.domain.LevelPolicy;

import java.util.List;

/** 레벨 정책 조회 경계. */
public interface LevelPolicyQueryRepository {

    List<LevelPolicy> findUpToLevel(int level);
}
