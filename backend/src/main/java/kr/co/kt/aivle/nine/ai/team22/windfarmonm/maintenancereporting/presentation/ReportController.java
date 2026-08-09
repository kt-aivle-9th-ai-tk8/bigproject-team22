package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation;

import jakarta.validation.Valid;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.Login;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.ReportCommandService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.ReportQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportSummaryResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto.CreateReportRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto.CreatedReportResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto.ReportDetailResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto.ReportSummaryResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto.UpdateReportRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 보고서 API. context-path(/api) 기준 /reports.
 * <p>
 * 열람 권한이 없는 보고서는 404 로 응답한다(존재 은닉) — 별도의 403 코드를 두지 않는다.
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportQueryService reportQueryService;
    private final ReportCommandService reportCommandService;

    /**
     * 리포트 초안 생성: POST /api/reports
     * <p>
     * 행만 만들고 <b>202 로 즉시 응답</b>한다. 본문 생성은 외부 에이전트가 이어서 수행하므로 식별자만 돌려준다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CreatedReportResponse>> createReport(
            @Valid @RequestBody CreateReportRequest request,
            @Login LoginMember member) {
        Long reportId = reportCommandService.create(member.userId(), member.isAdmin(), request.toCommand());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("보고서 생성을 접수했습니다.", CreatedReportResponse.of(reportId)));
    }

    /** 리포트 목록 조회: GET /api/reports (wind_farm_id / turbine_id / report_type 으로 필터링) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReportSummaryResponse>>> getReports(
            @RequestParam(name = "wind_farm_id", required = false) String windFarmId,
            @RequestParam(name = "turbine_id", required = false) String turbineId,
            @RequestParam(name = "report_type", required = false) ReportType reportType,
            @Login LoginMember member) {
        List<ReportSummaryResponse> body = reportQueryService.getReports(
                        member.userId(), member.isAdmin(),
                        toIdOrNull(windFarmId), toIdOrNull(turbineId), reportType).stream()
                .map(ReportSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 리포트 조회: GET /api/reports/{reportId} */
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getReport(@PathVariable String reportId,
                                                                      @Login LoginMember member) {
        ReportResult result = reportQueryService.getReport(member.userId(), member.isAdmin(), ApiIds.toLong(reportId));
        return ResponseEntity.ok(ApiResponse.success(ReportDetailResponse.from(result)));
    }

    /** 리포트 직접 수정: PATCH /api/reports/{reportId} */
    @PatchMapping("/{reportId}")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> updateReport(@PathVariable String reportId,
                                                                         @Valid @RequestBody UpdateReportRequest request,
                                                                         @Login LoginMember member) {
        ReportResult result = reportCommandService.updateContext(
                member.userId(), member.isAdmin(), ApiIds.toLong(reportId), request.context());
        return ResponseEntity.ok(ApiResponse.success("수정되었습니다.", ReportDetailResponse.from(result)));
    }

    /** 리포트 삭제: DELETE /api/reports/{reportId} */
    @DeleteMapping("/{reportId}")
    public ResponseEntity<ApiResponse<Void>> deleteReport(@PathVariable String reportId,
                                                          @Login LoginMember member) {
        reportCommandService.delete(member.userId(), member.isAdmin(), ApiIds.toLong(reportId));
        return ResponseEntity.ok(ApiResponse.success("삭제되었습니다.", null));
    }

    /** 선택 쿼리 파라미터. 미입력이면 조건 미적용, 값이 있으면 형식 검증을 거친다. */
    private static Long toIdOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : ApiIds.toLong(raw);
    }
}
