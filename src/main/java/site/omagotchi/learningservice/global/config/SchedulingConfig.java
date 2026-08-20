package site.omagotchi.learningservice.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 실행 활성화.
 *
 * <p>최상위 Application Class가 아니라 {@code global/config}에 두는 것은 Convention이다 —
 * Bootstrap Class는 실행과 {@code ConfigurationProperties} 등록만 맡는다
 * (10-backend-code-structure §2).</p>
 *
 * <p>끌 수 있게 해 둔 것이 중요하다. 스케줄러가 도는 통합 Test는 시간에 의존해 불안정해지고,
 * 여러 인스턴스를 띄우는 환경에서 특정 인스턴스만 배치를 맡기고 싶을 수도 있다.
 * 기본값은 켬이라 운영에서 설정을 빠뜨려 만료 정리가 멈추는 일은 없다.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "omagotchi.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
