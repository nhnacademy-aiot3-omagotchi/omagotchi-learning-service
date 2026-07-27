package site.omagotchi.learningservice.space.domain.exception;

import site.omagotchi.learningservice.global.exception.BusinessException;

public class InvalidSpaceCapacityException extends BusinessException {

    public InvalidSpaceCapacityException() {
        super(SpaceErrorCode.INVALID_CAPACITY);
    }
}
