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
 * 도메인 이벤트 리스너의 비동기 실행 설정.
 *
 * <p><b>{@code @EnableAsync}가 없으면 {@code @Async}는 조용히 무시된다.</b> 그러면 리스너가
 * 발행 스레드에서 그대로 실행되어, 정리·발송이 느리거나 실패할 때 원래 요청까지 끌고 간다 —
 * ADR space-team/0006이 비동기로 분리한 이유가 사라진다.</p>
 *
 * <p><b>전용 실행기를 두고 기본 실행기는 건드리지 않는다.</b> 이 Class가 빈 이름
 * {@code applicationTaskExecutor}를 차지하면 Spring Boot의 기본 실행기를 밀어내고,
 * 그 실행기는 {@code @Async}뿐 아니라 Spring MVC의 비동기 요청 처리도 함께 쓴다 —
 * 아래 {@code CallerRunsPolicy} 같은 도메인 이벤트용 결정이 HTTP 처리에까지 번진다.
 * {@link AsyncConfigurer#getAsyncExecutor()}를 재정의하지 않는 것도 같은 이유다.
 * 재정의하면 이 실행기가 {@code @Async} 전체의 기본값이 된다.</p>
 *
 * <p><b>아직 분리가 완성되지 않았다.</b> Boot의 기본 실행기 자동 구성은
 * {@code @ConditionalOnMissingBean(Executor.class)}이라, 아래 빈을 정의하는 것만으로 물러난다.
 * 그래서 지금은 기본 실행기가 사라지고 <b>남은 실행기가 이것뿐이라 결국 전역 기본값이 된다</b> —
 * 이름을 지정하지 않은 {@code @Async}도 여기로 온다.</p>
 *
 * <p>막는 방법은 {@code application.yaml}에 {@code spring.task.execution.mode: force} 한 줄이며
 * (Boot이 이 상황을 위해 둔 탈출구), 설정 반영이 보류되어 아직 넣지 않았다. 넣기 전까지 위
 * 문단의 의도는 <b>규약일 뿐 강제되지 않는다</b> — 기동도 로그도 정상이라 드러나지 않는다.</p>
 *
 * <p>그래서 리스너는 {@code @Async(AsyncConfig.EVENT_EXECUTOR)}처럼 <b>이름을 반드시
 * 지정해야 한다.</b> 빠뜨리면 여기 설정과 무관한 기본 실행기에서 돌고, 아래 큐 정책과
 * 스레드 이름 규칙이 적용되지 않는다.</p>
 *
 * <p>기본 설정을 쓰지 않는 것은 같은 ADR의 요구다(§5 "스레드 풀과 예외 로깅을 명시적으로
 * 관리해야 함"). 무제한 큐를 쓰면 소비가 밀릴 때 작업이 메모리에 계속 쌓이고 그 사실이
 * 드러나지 않는다. 여기서는 큐를 유한하게 두고 넘치면 <b>호출한 스레드가 직접 실행</b>하도록
 * 해, 밀리는 상황이 지연으로 드러나게 한다.</p>
 *
 * <p>예외 처리기를 명시하는 것이 핵심이다. {@code @Async} Method가 {@code void}를 반환하면
 * 던진 예외는 <b>아무도 받지 않는다</b> — 처리기가 없으면 팀 정리나 알림 발송이 실패해도
 * 로그 한 줄 남지 않는다. 이쪽은 실행기와 달리 전역이어야 맞다.</p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

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
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("비동기 처리에 실패했습니다. method={}", method.getName(), throwable);
    }
}
