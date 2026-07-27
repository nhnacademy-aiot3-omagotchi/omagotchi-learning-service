package site.omagotchi.learningservice.space.domain.exception;

import site.omagotchi.learningservice.global.exception.BusinessException;

public class SpaceNotFoundException extends BusinessException {

    public SpaceNotFoundException() {
        super(SpaceErrorCode.NOT_FOUND);
    }
}
