package site.omagotchi.learningservice.space.application.result;

import java.util.UUID;

/** 관리자에게 공개할 선택 기수의 현재 공간 이용자. */
public record SpacePresenceOccupantResult(
        UUID userId,
        String displayName
) {
}
