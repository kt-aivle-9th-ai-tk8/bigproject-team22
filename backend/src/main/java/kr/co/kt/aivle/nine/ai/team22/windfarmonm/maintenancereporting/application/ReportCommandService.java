package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.CreateReportCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.event.ReportGenerationRequested;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportAssetPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 보고서 생성/수정/삭제.
 * <p>
 * 생성은 <b>행만 만들고 즉시 반환</b>한다(202). 본문은 외부 에이전트가 만들어 완료 통지로 채우므로,
 * 이 서비스는 식별자를 확보하는 데까지만 책임진다.
 */
@Service
@RequiredArgsConstructor
public class ReportCommandService {

    private final ReportRepository reportRepository;
    private final ReportQueryService reportQueryService;
    private final ReportAssetPort assetPort;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 보고서 생성 요청. 생성된 식별자를 돌려준다.
     * <p>
     * 대상 단지에 접근할 수 없으면 접근 거부가 아니라 {@link ErrorCode#WIND_FARM_NOT_FOUND} 로 응답한다
     * (명세 요구: 담당이 아닌 사용자에게 단지의 존재를 드러내지 않는다).
     * <p>
     * 대상의 <b>존재·소속 정합성</b>은 앱에서 검사하지 않고 DB 제약에 맡긴다 — 단지/터빈 실재는 FK 가,
     * "터빈이 그 단지 소속인가"는 복합 FK 가 강제한다. 위반은 {@link DataIntegrityViolationException} 으로
     * 올라오며, 이를 {@link ErrorCode#INVALID_REPORT_TARGET}(400)로 번역한다. 정합성의 소유는 DB 이고
     * 앱은 예외를 사용자 오류로 옮기기만 한다.
     */
    @Transactional
    public Long create(Long userId, boolean admin, CreateReportCommand command) {
        validateReportType(command);
        validatePeriod(command);
        validateTurbineScope(command);
        requireAccessibleWindFarm(userId, admin, command.windFarmId());

        Report report = Report.request(
                command.reportType(),
                command.windFarmId(),
                command.turbineId(),
                command.periodStart(),
                command.periodEnd(),
                command.anomalyEventId(),
                userId);
        try {
            Long reportId = reportRepository.save(report).getId();
            // 커밋 후 리스너가 생성 파이프라인을 돈다(AFTER_COMMIT). 커밋 전이면 콜백이 행보다 먼저 도착한다.
            eventPublisher.publishEvent(new ReportGenerationRequested(reportId));
            return reportId;
        } catch (DataIntegrityViolationException e) {
            // 단지/터빈 FK 또는 (터빈,단지) 복합 FK 위반. 어느 참조가 문제인지는 구분하지 않는다.
            throw new BusinessException(ErrorCode.INVALID_REPORT_TARGET);
        }
    }

    /**
     * 결함 진단 보고서 행 생성(<b>내부 전용</b> — 점검 세션 생성이 같은 트랜잭션에서 부른다).
     * <p>
     * 공개 생성 API 와 달리 ① 인가·유형 검증을 하지 않고(호출측이 담당 인가를 이미 통과), ② <b>생성 파이프라인
     * 이벤트를 발행하지 않는다</b> — 결함 보고서의 본문은 결함 적재가 끝난 뒤에야 만들 수 있으므로, 생성 트리거는
     * 결함 적재 완료 시점(P5)이 담당한다. 여기서는 PENDING 행으로 자리만 잡는다.
     * 세션이 터빈 여러 대를 묶으므로 turbine_id 는 비운다.
     *
     * @param context 사용자가 넣은 보고서 참고사항(초기 본문). null 이면 비워 둔다
     */
    @Transactional
    public Long createDefectDiagnosis(Long windFarmId, LocalDateTime periodStart, LocalDateTime periodEnd,
                                      Long createdBy, String context) {
        Report report = Report.request(ReportType.DEFECT_DIAGNOSIS,
                windFarmId, null, periodStart, periodEnd, null, createdBy);
        if (context != null && !context.isBlank()) {
            report.editContext(context);
        }
        return reportRepository.save(report).getId();
    }

    /**
     * 결함 보고서 본문 생성 요청(<b>내부 전용</b> — 결함 적재 완료가 부른다).
     * 호출측 트랜잭션에 참여해 이벤트를 발행하고, 파이프라인은 그 트랜잭션이 커밋된 뒤 돈다(AFTER_COMMIT) —
     * 커밋 전 결함 데이터로 에이전트가 생성하는 것을 막는다.
     */
    @Transactional
    public void requestDefectGeneration(Long reportId) {
        eventPublisher.publishEvent(new ReportGenerationRequested(reportId));
    }

    /**
     * 본문 직접 수정.
     * <p>
     * 상태를 검사하지 않는다 — 생성에 실패해 PROCESSING 에 남은 보고서도 손댈 수 있어야 한다.
     */
    @Transactional
    public ReportResult updateContext(Long userId, boolean admin, Long reportId, String context) {
        Report report = reportQueryService.readViewable(userId, admin, reportId);
        report.editContext(context);
        return ReportResult.from(report);
    }

    @Transactional
    public void delete(Long userId, boolean admin, Long reportId) {
        Report report = reportQueryService.readViewable(userId, admin, reportId);
        reportRepository.delete(report);
    }

    private void validatePeriod(CreateReportCommand command) {
        if (command.periodStart() == null || command.periodEnd() == null
                || command.periodStart().isAfter(command.periodEnd())) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }
    }

    /** 공개 생성 API 는 사용자 요청 유형만 받는다. 결함·이상 보고서는 자동 경로 전용이다. */
    private void validateReportType(CreateReportCommand command) {
        if (!command.reportType().isUserRequestable()) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_TYPE);
        }
    }

    /** 유형과 대상의 정합성. 단지 보고서에 터빈이 붙거나 터빈 보고서에 터빈이 없으면 데이터가 어긋난다. */
    private void validateTurbineScope(CreateReportCommand command) {
        boolean turbineGiven = command.turbineId() != null;
        if (command.reportType().requiresTurbine() != turbineGiven) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 담당 단지 인가만 수행한다(존재 검사는 DB FK 가 하므로 여기서 하지 않는다).
     * ADMIN 은 제한이 없어 이 검사를 통과하며, 없는 단지를 지정하면 저장 시 FK 위반으로 400 이 된다.
     */
    private void requireAccessibleWindFarm(Long userId, boolean admin, Long windFarmId) {
        List<Long> viewable = assetPort.viewableWindFarmIds(userId, admin); // ADMIN 이면 null(제한 없음)
        if (viewable != null && !viewable.contains(windFarmId)) {
            throw new BusinessException(ErrorCode.WIND_FARM_NOT_FOUND);
        }
    }
}
