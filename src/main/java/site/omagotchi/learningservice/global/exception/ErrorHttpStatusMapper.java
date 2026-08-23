package site.omagotchi.learningservice.global.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorHttpStatusMapper {

    public static HttpStatus map(ErrorType type) {
        return switch (type) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case AUTHENTICATION -> HttpStatus.UNAUTHORIZED;
            case AUTHORIZATION -> HttpStatus.FORBIDDEN;
            case BAD_GATEWAY -> HttpStatus.BAD_GATEWAY;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
