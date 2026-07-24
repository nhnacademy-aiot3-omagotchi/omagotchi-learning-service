package site.omagotchi.learningservice.space.domain.exception;

public class DuplicateSpaceNameException extends RuntimeException {
    public DuplicateSpaceNameException(String message) {
        super(message);
    }
}
