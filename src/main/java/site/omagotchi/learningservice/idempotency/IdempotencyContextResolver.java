package site.omagotchi.learningservice.idempotency;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class IdempotencyContextResolver {

    public IdempotencyContext resolve(Object[] arguments) {
        List<IdempotencyContext> contexts = Arrays.stream(arguments)
                .filter(IdempotencyContext.class::isInstance)
                .map(IdempotencyContext.class::cast)
                .toList();

        if (contexts.size() != 1) {
            throw new IllegalStateException(
                    "@Idempotent 메서드는 정확히 하나의 IdempotencyContext 인자를 가져야 합니다."
            );
        }

        return contexts.getFirst();
    }
}
