package site.omagotchi.learningservice.space.domain.exception;

public class SpaceNotFoundException extends RuntimeException {

    public SpaceNotFoundException(String message) {
        super(message);
    }
}