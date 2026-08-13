package site.omagotchi.learningservice.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 도메인 이벤트 리스너의 비동기 실행 설정 (ADR space-team/0006, 0012).
 *
 * <p>도메인 이벤트 전용 실행기를 두고 Boot의 기본 실행기({@code applicationTaskExecutor})와
 * 분리한다. 아래 {@code CallerRunsPolicy} 같은 이벤트용 결정이 HTTP 처리에 번지지 않게 하려는
 * 것이며, 분리를 실제로 성립시키는 것은 {@code application.yaml}의
 * {@code spring.task.execution.mode: force}다.</p>
 *
 * <p><b>아래 넷 중 하나라도 어기면 분리가 조용히 깨지거나 기동이 실패한다.</b>
 * 각각의 이유와 근거 Class는 ADR 0012 §4·§6에 있다.</p>
 * <ul>
 *   <li>리스너는 {@code @Async(AsyncConfig.EVENT_EXECUTOR)}로 <b>이름을 지정한다.</b>
 *       이름을 생략하는 것은 "기본 실행기를 쓰겠다"는 선언이다</li>
 *   <li>이 Class는 {@code AsyncConfigurer}를 <b>직접 구현하지 않는다</b> —
 *       구현하면 {@code force}에서 기동이 실패한다</li>
 *   <li>{@link AsyncConfigurer#getAsyncExecutor()}를 <b>재정의하지 않는다</b> —
 *       재정의하면 이 실행기가 {@code @Async} 전체의 기본값이 된다</li>
 *   <li>{@code spring.task.execution.mode: force}를 <b>지우지 않는다</b> —
 *       빼도 기동도 로그도 정상이라 드러나지 않는다</li>
 * </ul>
 *
 * <p>{@code AsyncConfigTest}가 넷을 모두 고정한다. {@code @EnableAsync}가 빠지면
 * {@code @Async}는 조용히 무시되어 리스너가 발행 스레드에서 그대로 실행된다.</p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 도메인 이벤트 리스너 전용 실행기의 빈 이름.
     *
     * <p>{@code @Async}에 그대로 넘긴다. 문자열을 직접 적지 않고 상수를 쓰는 이유는 오타가
     * 기동 시점이 아니라 <b>이벤트가 처음 발행될 때</b> 드러나기 때문이다 — 그때는 이미
     * 리스너가 엉뚱한 실행기에서 돌고 있다.</p>
     */
    public static final String EVENT_EXECUTOR = "eventTaskExecutor";

    /**
     * 도메인 이벤트 리스너 전용 실행기.
     *
     * <p>{@code initialize()}를 직접 부르지 않는다. {@link ThreadPoolTaskExecutor}가
     * {@code InitializingBean}이라 컨테이너가 {@code afterPropertiesSet()}에서 호출하며,
     * 여기서 한 번 더 부르면 스레드 풀이 두 개 만들어지고 앞의 것이 회수되지 않는다.</p>
     */
    @Bean(EVENT_EXECUTOR)
    public ThreadPoolTaskExecutor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        // 스레드 이름이 곧 로그의 출처다. 기본값이면 어느 리스너가 남긴 로그인지 알 수 없다.
        executor.setThreadNamePrefix("omagotchi-event-");
        // 큐가 차면 호출 스레드에서 실행한다. 버리면 정리가 조용히 유실되고,
        // 큐를 무제한으로 두면 밀리는 사실 자체가 드러나지 않는다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 종료 시 진행 중인 정리를 끝까지 기다린다 — 중간에 끊기면 팀·점유가 어중간한 상태로 남는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * {@code void} 반환 {@code @Async} Method의 예외를 기록한다.
     *
     * <p>{@link SimpleAsyncUncaughtExceptionHandler}를 그대로 쓰지 않는 이유는 어느 Method가
     * 실패했는지 함께 남기기 위해서다 — 리스너가 여럿이면 stack trace만으로는 구분이 어렵다.</p>
     *
     * <p>바깥 Class가 구현하지 않고 <b>별도 빈</b>인 것, {@link AsyncConfigurer#getAsyncExecutor()}를
     * 재정의하지 않고 {@code null}로 두는 것 모두 의도다 (ADR 0012 §4 결정 2·3).</p>
     */
    @Bean
    public AsyncConfigurer asyncUncaughtExceptionConfigurer() {
        return new AsyncConfigurer() {

            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                return (throwable, method, params) ->
                        log.error("비동기 처리에 실패했습니다. method={}", method.getName(), throwable);
            }
        };
    }
}
