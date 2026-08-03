package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.WindFarmQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerQuery;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WindFarmDetailResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto.PowerPointResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto.WindFarmDetailResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto.WindFarmSummaryResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.Login;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
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
 * 풍력단지 관제 API. context-path(/api) 기준 /wind-farms.
 */
@RestController
@RequestMapping("/wind-farms")
@RequiredArgsConstructor
public class WindFarmController {

    private final WindFarmQueryService windFarmQueryService;

    /**
     * 담당 풍력단지 통합조회: GET /api/wind-farms
     * top-n / location / weather / power 쿼리로 반환 항목/필드를 제어한다(플래그는 0/1, 미입력 시 미출력).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WindFarmSummaryResponse>>> getWindFarms(
            @RequestParam(name = "top-n", required = false) Integer topN,
            @RequestParam(name = "location", required = false) Integer location,
            @RequestParam(name = "weather", required = false) Integer weather,
            @RequestParam(name = "power", required = false) Integer power,
            @Login LoginMember member) {
        List<WindFarmSummaryResponse> body = windFarmQueryService
                .getWindFarms(member.userId(), topN, flag(location), flag(weather), flag(power)).stream()
                .map(WindFarmSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 단일 풍력단지 정보 조회: GET /api/wind-farms/{windFarmId} */
    @GetMapping("/{windFarmId}")
    public ResponseEntity<ApiResponse<WindFarmDetailResponse>> getWindFarm(@PathVariable String windFarmId,
                                                                          @Login LoginMember member) {
        WindFarmDetailResult result = windFarmQueryService.getWindFarm(member.userId(), isAdmin(member), ApiIds.toLong(windFarmId));
        return ResponseEntity.ok(ApiResponse.success(WindFarmDetailResponse.from(result)));
    }

    /** 풍력단지 발전량 조회: GET /api/wind-farms/{windFarmId}/power */
    @GetMapping("/{windFarmId}/power")
    public ResponseEntity<ApiResponse<List<PowerPointResponse>>> getWindFarmPower(
            @PathVariable String windFarmId,
            @RequestParam(name = "start_time", required = false) String startTime,
            @RequestParam(name = "end_time", required = false) String endTime,
            @RequestParam(name = "term", required = false) String term,
            @Login LoginMember member) {
        PowerQuery query = PowerQuery.of(startTime, endTime, term);
        List<PowerPointResponse> body = windFarmQueryService
                .getWindFarmPower(member.userId(), isAdmin(member), ApiIds.toLong(windFarmId), query).stream()
                .map(PowerPointResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /** 0/1 플래그 파싱. 1 인 경우에만 true, 그 외(미입력/0/기타)는 false. */
    private boolean flag(Integer value) {
        return value != null && value == 1;
    }

    private boolean isAdmin(LoginMember member) {
        return member.role() == Role.ADMIN;
    }
}
