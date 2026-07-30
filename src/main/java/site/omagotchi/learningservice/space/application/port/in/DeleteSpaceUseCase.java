package site.omagotchi.learningservice.space.application.port.in;

import java.util.UUID;

public interface DeleteSpaceUseCase {

    void delete(
            Long spaceId,
            UUID actorUserId,
            String globalRole
    );
}
