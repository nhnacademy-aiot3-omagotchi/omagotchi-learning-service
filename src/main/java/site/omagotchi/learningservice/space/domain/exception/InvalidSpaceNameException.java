package site.omagotchi.learningservice.space.domain.exception;

// 이름 오류
public class InvalidSpaceNameException extends RuntimeException {
    public InvalidSpaceNameException(String message) {
        super(message);
    }
}