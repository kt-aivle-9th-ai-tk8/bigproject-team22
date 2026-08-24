package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.TurbineQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerQuery;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.TurbineDetailResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto.PowerPointResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto.TurbineViewerResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.Login;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 터빈 관제 API. context-path(/api) 기준 /turbines.
 */
@RestController
@RequestMapping("/turbines")
@RequiredArgsConstructor
public class TurbineController {

    private final TurbineQueryService turbineQueryService;

    /** 터빈 3D 뷰어 통합조회: GET /api/turbines/{turbineId} */
    @GetMapping("/{turbineId}")
    public ResponseEntity<ApiResponse<TurbineViewerResponse>> getTurbine(@PathVariable String turbineId,
                                                                         @Login LoginMember member) {
        TurbineDetailResult result = turbineQueryService.getTurbine(member.userId(), member.isAdmin(), ApiIds.toLong(turbineId));
        return ResponseEntity.ok(ApiResponse.success(TurbineViewerResponse.from(result)));
    }

    /** 터빈 발전량 조회: GET /api/turbines/{turbineId}/power */
    @GetMapping("/{turbineId}/power")
    public ResponseEntity<ApiResponse<List<PowerPointResponse>>> getTurbinePower(
            @PathVariable String turbineId,
            @RequestParam(name = "start_time", required = false) String startTime,
            @RequestParam(name = "end_time", required = false) String endTime,
            @RequestParam(name = "term", required = false) String term,
            @Login LoginMember member) {
        PowerQuery query = PowerQuery.of(startTime, endTime, term);
        List<PowerPointResponse> body = turbineQueryService
                .getTurbinePower(member.userId(), member.isAdmin(), ApiIds.toLong(turbineId), query).stream()
                .map(PowerPointResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
