package site.omagotchi.learningservice.space.application.port;

import site.omagotchi.learningservice.space.application.result.SpaceLabReductionView;

import java.util.Optional;

/** 활성 LAB 감소 명령이 잠글 기수를 결정하는 비잠금 조회 경계. */
public interface SpaceLabReductionQueryPort {

    Optional<SpaceLabReductionView> find(Long spaceId);
}
