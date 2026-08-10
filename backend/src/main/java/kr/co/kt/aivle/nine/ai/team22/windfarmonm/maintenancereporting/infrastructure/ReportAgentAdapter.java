package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationTarget;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportGenerationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link ReportGenerationPort} 어댑터. report-agent 의 {@code POST /api/v1/reports {report_type, event_id}} 를
 * <b>동기</b>로 호출해 생성 본문(draft)을 받는다(에이전트가 웹훅 콜백을 지원하지 않으므로).
 * <p>
 * base-url 미설정이면 호출하지 않고 비활성으로 보고한다. 타임아웃은 report-agent 전용({@link ReportAgentProperties})
 * — 연결은 fail-fast, 읽기는 LLM 생성이 길어 넉넉히. 호출 실패/오류는 예외로 올리지 않고
 * {@link ReportGenerationResult#notGenerated()} 로 돌려준다(보고서는 PROCESSING 으로 남고 회수 가능).
 * HTTP 는 Boot 자동구성 {@link RestClient.Builder} 를 주입받아 타임아웃만 지정한다(KMA 어댑터와 동일 관례).
 */
@Slf4j
@Component
@EnableConfigurationProperties(ReportAgentProperties.class)
public class ReportAgentAdapter implements ReportGenerationPort {

    private static final String GENERATE_PATH = "/api/v1/reports";

    private final ReportAgentProperties properties;
    private final RestClient restClient;

    public ReportAgentAdapter(ReportAgentProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl() != null ? properties.baseUrl() : "")
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Override
    public boolean isEnabled() {
        return properties.baseUrl() != null && !properties.baseUrl().isBlank();
    }

    @Override
    public ReportGenerationResult generate(ReportGenerationTarget target) {
        if (!isEnabled()) {
            return ReportGenerationResult.notGenerated();
        }
        try {
            AgentResponse response = restClient.post()
                    .uri(GENERATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AgentRequest(target.agentType(), target.eventId()))
                    .retrieve()
                    .body(AgentResponse.class);
            if (response == null) {
                return ReportGenerationResult.notGenerated();
            }
            return new ReportGenerationResult(response.found(), response.draft(), response.verdict());
        } catch (RuntimeException e) {
            // 대상없음(404)·LLM 실패(502)·타임아웃 등 모두 여기로. 보고서는 PROCESSING 유지.
            // throwable 을 넘겨 stack trace·원인(HTTP status/timeout)을 보존한다(SageMakerInvoker 관례).
            log.warn("report-agent 생성 호출 실패(type={}, eventId={})",
                    target.agentType(), target.eventId(), e);
            return ReportGenerationResult.notGenerated();
        }
    }

    /** 에이전트 요청 본문. 전역 snake_case 전략으로 {@code {report_type, event_id}} 로 직렬화된다. */
    private record AgentRequest(String reportType, int eventId) {
    }

    /** 에이전트 응답 중 채택 판단·로깅에 쓰는 필드만. 나머지 필드는 무시된다(unknown 허용). */
    private record AgentResponse(boolean found, String verdict, String draft) {
    }
}
