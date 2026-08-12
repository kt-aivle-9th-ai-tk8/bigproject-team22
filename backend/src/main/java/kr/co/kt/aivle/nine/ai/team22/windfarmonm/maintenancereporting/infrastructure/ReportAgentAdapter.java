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
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link ReportGenerationPort} 어댑터. report-agent 의
 * {@code POST /api-internal/reports {report_type, event_id, period_start, period_end}} 를 <b>동기</b>로 호출해
 * 제목({@code title})과 본문({@code context})을 받는다(에이전트가 웹훅 콜백을 지원하지 않으므로).
 * <p>
 * base-url 미설정이면 호출하지 않고 비활성으로 보고한다. 타임아웃은 report-agent 전용({@link ReportAgentProperties})
 * — 연결은 fail-fast, 읽기는 LLM 생성이 길어 넉넉히. 호출 실패/오류는 예외로 올리지 않고
 * {@link ReportGenerationResult#notGenerated()} 로 돌려준다(보고서는 PROCESSING 으로 남고 회수 가능).
 * <p>
 * 상태코드: 404(대상없음)·422(잘못된 유형)·502(LLM 실패)는 모두 회수 가능 실패로 처리한다.
 * 429(동시 생성 상한, {@code Retry-After: 30})는 지금은 회수 가능 실패로만 두고 로그로 구분한다 —
 * Retry-After 를 존중하는 재시도는 후속 과제(TODO). HTTP 는 Boot 자동구성 {@link RestClient.Builder} 를 주입받아
 * 타임아웃만 지정한다(KMA 어댑터와 동일 관례).
 */
@Slf4j
@Component
@EnableConfigurationProperties(ReportAgentProperties.class)
public class ReportAgentAdapter implements ReportGenerationPort {

    private static final String GENERATE_PATH = "/api-internal/reports";

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
                    .body(new AgentRequest(target.agentType(), target.eventId(),
                            target.periodStart(), target.periodEnd()))
                    .retrieve()
                    .body(AgentResponse.class);
            if (response == null) {
                return ReportGenerationResult.notGenerated();
            }
            if (StringUtils.hasText(response.error())) {
                log.warn("report-agent 응답 error(type={}, eventId={}): {}",
                        target.agentType(), target.eventId(), response.error());
            }
            return new ReportGenerationResult(
                    response.found(), response.title(), response.context(), response.verdict());
        } catch (HttpClientErrorException.TooManyRequests e) {
            // 429: 동시 생성 상한. 지금은 회수 가능 실패로 두고 로그로 구분(후속: Retry-After 존중 재시도).
            log.warn("report-agent 429(동시 생성 상한) — 재요청 필요(type={}, eventId={}, retryAfter={})",
                    target.agentType(), target.eventId(),
                    e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null);
            return ReportGenerationResult.notGenerated();
        } catch (RestClientResponseException e) {
            // 404 대상없음 / 422 잘못된 유형 / 502 LLM 실패 등. 보고서는 PROCESSING 유지(회수 가능).
            log.warn("report-agent 오류 응답 status={}(type={}, eventId={})",
                    e.getStatusCode(), target.agentType(), target.eventId(), e);
            return ReportGenerationResult.notGenerated();
        } catch (RuntimeException e) {
            // 타임아웃·연결 실패 등. throwable 을 넘겨 stack trace·원인을 보존한다(SageMakerInvoker 관례).
            log.warn("report-agent 생성 호출 실패(type={}, eventId={})",
                    target.agentType(), target.eventId(), e);
            return ReportGenerationResult.notGenerated();
        }
    }

    /** 에이전트 요청 본문. 전역 snake_case 전략으로 {@code {report_type, event_id, period_start, period_end}} 로 직렬화된다. */
    private record AgentRequest(String reportType, long eventId, String periodStart, String periodEnd) {
    }

    /** 에이전트 응답 중 채택 판단·로깅에 쓰는 필드만(나머지 params/retry_count/issues/warnings 는 무시). */
    private record AgentResponse(boolean found, String verdict, String title, String context, String error) {
    }
}
