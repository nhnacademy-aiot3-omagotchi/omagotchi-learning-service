package site.omagotchi.learningservice.space.domain.exception;

import site.omagotchi.learningservice.global.exception.BusinessException;

public class InvalidSpaceNameException extends BusinessException {

    public InvalidSpaceNameException() {
        super(SpaceErrorCode.INVALID_NAME);
    }
}
