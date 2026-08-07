package site.omagotchi.learningservice.idempotency;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE) // aspect 중에서 가장 높은 우선순위 적용
public class IdempotencyAspect {
    private final TransactionTemplate idempotencyTransactionTemplate;

    public Object execute(
            ProceedingJoinPoint joinPoint
    ) {
        return null;
    }
}
