package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * report-agent 접속 설정. {@code report-agent.*} 프로퍼티로 주입된다.
 * <p>
 * base-url 은 지금은 ALB URL(테스트), 이후 AWS Service Connect 내부 DNS 로 <b>값만</b> 교체한다(코드 불변).
 * 미설정이면 어댑터가 생성을 건너뛴다. read-timeout 은 LLM 생성이 길어 넉넉히 잡는다(연결은 fail-fast).
 * <p>
 * 주의: ALB 경유 시 ALB idle timeout(기본 60s)이 이 read-timeout 보다 먼저 커넥션을 끊을 수 있다 —
 * 긴 생성은 Service Connect(내부 DNS, ALB 우회)로 전환한 뒤에 안전하다.
 */
@ConfigurationProperties(prefix = "report-agent")
public record ReportAgentProperties(
        String baseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("300s") Duration readTimeout
) {
}
