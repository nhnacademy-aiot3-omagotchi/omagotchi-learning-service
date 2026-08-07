package site.omagotchi.learningservice.idempotency.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/*
 * @Configuration(proxyBeanMethods = false)
 * 설정 클래스에서 다른 bean 메서드를 직접 호출할 일이 없다면 프록시 생성 단계를 건너뛰어
 * 어플리케이션의 구동 속도를 높이고 메모리 사용량을 줄일 수 있는 최적화 방식
 */
@Configuration(proxyBeanMethods = false)
public class IdempotencyTransactionConfig {

    @Bean
    public TransactionTemplate idempotencyTransactionTemplate(
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        // Transaction template의 propagation과 isolation 레벨을 설정합니다.
        // PROPAGATION_REQUIRED: 부모 트랜잭션이 있으면 합류, 없으면 새로운 트랜잭션
        // ISOLATION_DEFAULT: Isolation level은 DB의 기본 격리 수준을 따름
        // postgreSQL이라 READ COMMITTED
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_DEFAULT);

        return template;
    }
}
