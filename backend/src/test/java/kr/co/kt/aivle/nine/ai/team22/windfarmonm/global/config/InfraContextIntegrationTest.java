package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.S3ObjectStorage;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.SageMakerInvoker;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.support.IntegrationTestSupport;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기반 인프라 설정의 기동 보장.
 * <p>
 * 이 테스트가 지키는 계약은 하나다 — <b>AWS·에이전트 설정이 전혀 없어도 애플리케이션 컨텍스트가 뜬다.</b>
 * 외부 클라이언트를 빈 생성 시점에 만들면 자격증명이 없는 환경(로컬·CI)에서 기동이 실패하는데,
 * 과거 리포트 에이전트가 정확히 그 방식으로 배포 롤백을 낸 적이 있다. 지연 초기화가 풀리면 여기서 잡힌다.
 */
class InfraContextIntegrationTest extends IntegrationTestSupport {

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("AWS 설정이 전무해도 컨텍스트가 기동하고 게이트웨이 빈이 미설정 상태로 존재한다")
    void contextLoads_withoutAwsConfiguration() {
        S3ObjectStorage storage = context.getBean(S3ObjectStorage.class);
        SageMakerInvoker invoker = context.getBean(SageMakerInvoker.class);

        assertThat(storage.isConfigured()).isFalse();
        assertThat(invoker.isAnomalyEndpointConfigured()).isFalse();
    }

    @Test
    @DisplayName("ShedLock LockProvider 가 Redis 로 구성된다")
    void lockProviderIsConfigured() {
        assertThat(context.getBean(LockProvider.class)).isNotNull();
    }
}
