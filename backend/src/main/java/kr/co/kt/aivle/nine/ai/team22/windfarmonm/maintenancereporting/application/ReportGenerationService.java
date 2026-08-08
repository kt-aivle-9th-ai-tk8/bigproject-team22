package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationTarget;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportTargetInfo;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportAssetPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 보고서 생성 파이프라인의 <b>트랜잭션 경계</b> 두 조각을 제공한다: 상태를 PROCESSING 으로 올리며
 * 에이전트 호출에 필요한 대상/제목을 계산하는 {@link #markProcessing}, 그리고 회신 본문을 적재하는
 * {@link #applyGenerated}. 그 사이의 <b>긴 외부 호출은 트랜잭션 밖</b>에서 일어나야 하므로(DB 커넥션을
 * 붙잡지 않도록), 오케스트레이션은 {@link ReportGenerationListener} 가 하고 이 서비스는 두 메서드를
 * <b>외부에서</b> 호출받는다(self-invocation 이면 @Transactional 프록시가 적용되지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final ReportRepository reportRepository;
    private final ReportAssetPort assetPort;

    /** {@link #markProcessing} 이 돌려주는, 에이전트 호출에 필요한 준비물. */
    public record Dispatch(ReportGenerationTarget target, String title) {
    }

    /**
     * 보고서를 PROCESSING 으로 전이하고, 에이전트 호출용 대상({@link ReportGenerationTarget})과 제목을 계산한다.
     * 대상 표시정보(단지명/터빈코드) 조회가 필요하므로 트랜잭션 안에서 함께 한다.
     *
     * @return 준비물. 보고서가 없으면 {@code null}(경쟁 삭제 등).
     */
    @Transactional
    public Dispatch markProcessing(Long reportId) {
        Report report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            return null; // 생성요청과 파이프라인 사이에 삭제된 경우 — 조용히 종료
        }
        report.markProcessing();
        ReportTargetInfo info = assetPort.resolveTargetInfo(report.getWindFarmId(), report.getTurbineId());
        ReportGenerationTarget target = new ReportGenerationTarget(
                report.getReportType().agentType(), resolveEventId(report, info));
        return new Dispatch(target, buildTitle(report, info));
    }

    /** 에이전트가 회신한 본문을 적재하고 GENERATED 로 완료 처리한다(BE 가 직접 RDS 에 쓴다). */
    @Transactional
    public void applyGenerated(Long reportId, String title, String context) {
        reportRepository.findById(reportId)
                .ifPresent(report -> report.complete(title, context)); // 관리 엔티티 — dirty checking 으로 flush
    }

    /**
     * 에이전트 event_id 매핑. 유형별로 가리키는 대상이 다르다(에이전트 계약).
     * 값이 int 라 id 를 좁혀 담지만, 현 데이터 규모에선 문제없다.
     */
    private int resolveEventId(Report report, ReportTargetInfo info) {
        return switch (report.getReportType()) {
            case TURBINE_OPERATION -> parseTurbineNumber(info.turbineCode()); // "U2" → 2
            case WIND_FARM_OPERATION -> report.getWindFarmId().intValue();     // 에이전트가 무시(단일 단지)
            case DEFECT_DIAGNOSIS -> report.getId().intValue();                // event_id = report_id
            case ANOMALY_EVENT -> report.getAnomalyEventId() == null ? 0 : report.getAnomalyEventId().intValue();
        };
    }

    /** 터빈 코드에서 번호를 뽑는다(예: {@code U2} → 2). 숫자가 없으면 0(에이전트가 대상없음으로 처리, 로깅됨). */
    private int parseTurbineNumber(String turbineCode) {
        if (turbineCode == null) {
            return 0;
        }
        Matcher m = DIGITS.matcher(turbineCode);
        if (m.find()) {
            return Integer.parseInt(m.group());
        }
        log.warn("터빈 코드에서 번호를 뽑지 못했다: {}", turbineCode);
        return 0;
    }

    /**
     * 제목을 '날짜 + 대상 + 종류'로 조립한다.
     * <p>
     * TODO(agent): 제목은 본래 에이전트가 생성해 완료 응답에 담아 주는 것이 바람직하다. 현재 에이전트의
     *   ReportResponse 에는 title 필드가 없어 BE 가 임시로 만든다. 개선판 에이전트가 title 을 전달하면
     *   이 조립 로직을 제거하고 그 값을 그대로 쓸 것.
     */
    private String buildTitle(Report report, ReportTargetInfo info) {
        String period = fmt(report.getPeriodStart()) + "–" + fmt(report.getPeriodEnd());
        String target = report.getReportType().requiresTurbine()
                ? (info.turbineCode() != null ? info.turbineCode() : "터빈 " + report.getTurbineId())
                : (info.windFarmName() != null ? info.windFarmName() : "단지 " + report.getWindFarmId());
        return "[%s] %s %s 보고서".formatted(period, target, report.getReportType().koreanLabel());
    }

    private static String fmt(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DATE);
    }
}
