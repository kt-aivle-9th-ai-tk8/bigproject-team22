package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행 설정. 보고서 생성 파이프라인이 에이전트를 <b>동기 호출</b>하되 요청 스레드(및 프론트→BE ALB)를
 * 붙잡지 않도록, 별도 executor 에서 돌린다. 웹훅/아웃박스 같은 무거운 비동기 기작은 쓰지 않는다 —
 * "요청 스레드 밖에서 동기 호출"만 하는 최소 구성이다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 보고서 생성 전용 executor. 동시 생성이 폭주하지 않도록 풀·큐를 작게 잡는다.
     * <p>
     * 배포(롤링) 시 진행 중이던 생성이 통째로 유실되지 않도록 종료 시 완료를 기다린다
     * ({@code waitForTasksToCompleteOnShutdown} + 유예). 유예를 넘겨 죽은 건은 FAILED 상태가 없어
     * PROCESSING 으로 남고, PATCH·재요청으로 회수한다(후속 복구 잡은 고도화 과제).
     */
    /**
     * 썸네일 생성 전용 executor. <b>스레드 1개</b>다 — 원본을 서브샘플링해도 장당 수십 MB 가 잡히므로
     * 여러 점검을 동시에 갈면 힙이 위험하다. 목록 표시용 파생물이라 처리량보다 안정성이 우선이다.
     * <p>
     * 큐가 메모리에 있어 재시작 시 대기분은 사라진다. 점검 단위 재실행 엔드포인트가 그 회수 경로다
     * (생성은 멱등이라 다시 돌려도 안전하다).
     */
    @Bean("thumbnailExecutor")
    public ThreadPoolTaskExecutor thumbnailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("thumbnail-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("report-gen-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
