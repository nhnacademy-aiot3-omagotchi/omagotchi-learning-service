package site.omagotchi.learningservice.space.application.result;

import java.util.List;

/** 공간 전체 현재 인원과 선택 기수에 공개 가능한 명단. */
public record SpacePresenceDetailResult(
        Long spaceId,
        long totalCount,
        long cohortCount,
        long otherCohortCount,
        List<SpacePresenceOccupantResult> occupants
) {
}
