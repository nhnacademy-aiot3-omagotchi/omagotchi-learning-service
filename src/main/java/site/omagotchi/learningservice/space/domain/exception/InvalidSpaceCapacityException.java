package site.omagotchi.learningservice.space.domain.exception;

// 수용 인원 오류
public class InvalidSpaceCapacityException extends RuntimeException {
    public InvalidSpaceCapacityException(String message) {
        super(message);
    }
}