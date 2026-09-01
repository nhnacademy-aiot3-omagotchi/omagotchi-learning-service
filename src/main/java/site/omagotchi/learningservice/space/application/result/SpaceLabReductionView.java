package site.omagotchi.learningservice.space.application.result;

/** 기수 선행 잠금 여부를 결정하기 위한 비관리 스칼라 스냅샷. */
public record SpaceLabReductionView(
        Long spaceId,
        Long cohortId,
        boolean activeLab
) {
}
