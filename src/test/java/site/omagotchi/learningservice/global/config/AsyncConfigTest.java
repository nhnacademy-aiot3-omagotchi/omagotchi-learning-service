package site.omagotchi.learningservice.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도메인 이벤트 실행기와 Boot 기본 실행기의 분리를 고정한다 (ADR space-team/0012 §6).
 *
 * <p>이 분리는 코드가 아니라 <b>설정 한 줄</b>에 달려 있고, 빼도 기동과 로그가 정상이라
 * 드러나지 않는다 — 이름 없는 {@code @Async}가 조용히 이벤트 실행기로 옮겨 갈 뿐이다.
 * 그래서 회귀 방어를 사람 눈이 아니라 여기 둔다.</p>
 */
@DisplayName("비동기 실행기 설정")
class AsyncConfigTest {

    private static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(AsyncConfig.class);

    /**
     * 실제 배포 설정을 그대로 검증한다.
     *
     * <p>나머지 테스트는 {@code force}가 무엇을 하는지만 보이고, 그 값이
     * {@code application.yaml}에 실제로 있는지는 확인하지 못한다 — 누군가 지우면 여기서만
     * 빨갛게 뜬다. Context를 띄우지 않는 것은 yaml이 기본값 없는 placeholder를 쓰기 때문이다.</p>
     */
    @Test
    @DisplayName("application.yaml이 기본 실행기 자동 구성을 강제한다")
    void applicationYamlForcesDefaultExecutorAutoConfiguration() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"));

        Object mode = sources.stream()
                .map(source -> source.getProperty("spring.task.execution.mode"))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);

        assertThat(mode)
                .as("spring.task.execution.mode를 지우면 applicationTaskExecutor가 사라져 "
                        + "이름 없는 @Async가 이벤트 실행기로 간다")
                .isEqualTo("force");
    }

    @Test
    @DisplayName("force면 이벤트 실행기와 기본 실행기가 함께 등록된다")
    void forceModeKeepsBothExecutors() {
        contextRunner
                .withPropertyValues("spring.task.execution.mode=force")
                .run(context -> {
                    assertThat(context).hasBean(APPLICATION_TASK_EXECUTOR);
                    assertThat(context).hasBean(AsyncConfig.EVENT_EXECUTOR);

                    // 같은 인스턴스면 분리가 성립하지 않는다 — 이벤트용 CallerRunsPolicy와
                    // 스레드 이름 규칙이 HTTP 비동기 처리에까지 적용된다.
                    assertThat(context.getBean(APPLICATION_TASK_EXECUTOR, Executor.class))
                            .isNotSameAs(context.getBean(AsyncConfig.EVENT_EXECUTOR, Executor.class));
                });
    }

    /**
     * 분리의 실제 효과 — 이름 없는 {@code @Async}가 어디로 가는가.
     *
     * <p>빈이 둘 다 있다는 것만으로는 부족하다. Boot이 {@link AsyncConfigurer}를 감싸
     * {@code getAsyncExecutor()}에 {@code applicationTaskExecutor}를 <b>이름으로</b> 채워야
     * {@code Executor}가 둘인 상황에서 타입 조회가 모호해지지 않는다.</p>
     */
    @Test
    @DisplayName("force면 이름 없는 @Async가 이벤트 실행기가 아니라 기본 실행기로 간다")
    void forceModeRoutesUnnamedAsyncToDefaultExecutor() {
        contextRunner
                .withPropertyValues("spring.task.execution.mode=force")
                .run(context -> {
                    AsyncConfigurer configurer = context.getBean(AsyncConfigurer.class);

                    assertThat(configurer.getAsyncExecutor())
                            .isSameAs(context.getBean(APPLICATION_TASK_EXECUTOR, Executor.class));
                });
    }

    /**
     * Boot의 래핑이 우리 예외 처리기를 삼키지 않는지 확인한다.
     *
     * <p>{@code void} 반환 {@code @Async}의 예외는 처리기가 없으면 <b>아무 데도 남지 않는다.</b>
     * 래퍼가 위임 대신 기본 처리기를 쓰기 시작하면 팀 정리·알림 발송 실패가 조용히 사라진다.</p>
     */
    @Test
    @DisplayName("force여도 예외 처리기는 우리 것이 유지된다")
    void forceModeKeepsCustomUncaughtExceptionHandler() {
        contextRunner
                .withPropertyValues("spring.task.execution.mode=force")
                .run(context -> {
                    AsyncConfigurer configurer = context.getBean(AsyncConfigurer.class);

                    assertThat(configurer.getAsyncUncaughtExceptionHandler())
                            .isNotNull()
                            .isNotInstanceOf(SimpleAsyncUncaughtExceptionHandler.class);
                });
    }

    /**
     * 대조군 — {@code force}가 없으면 무슨 일이 벌어지는지 고정한다.
     *
     * <p>이 테스트가 실패한다면 Boot이 조건을 바꾼 것이므로, {@code force}가 여전히 필요한지
     * 다시 판단해야 한다. 위 테스트만 있으면 "설정이 아직도 필요한가"를 알 수 없다.</p>
     */
    @Test
    @DisplayName("[대조군] 기본값(auto)에서는 이벤트 실행기가 유일한 실행기가 되어 전역 기본값이 된다")
    void autoModeLetsEventExecutorBecomeTheOnlyExecutor() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(APPLICATION_TASK_EXECUTOR);
            assertThat(context.getBeanNamesForType(Executor.class))
                    .containsExactly(AsyncConfig.EVENT_EXECUTOR);
        });
    }
}
