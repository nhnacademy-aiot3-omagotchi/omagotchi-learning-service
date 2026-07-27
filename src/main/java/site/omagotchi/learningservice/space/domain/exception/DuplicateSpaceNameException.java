package site.omagotchi.learningservice.space.domain.exception;

import site.omagotchi.learningservice.global.exception.BusinessException;

public class DuplicateSpaceNameException extends BusinessException {

    public DuplicateSpaceNameException() {
        super(SpaceErrorCode.DUPLICATE_NAME);
    }
}
