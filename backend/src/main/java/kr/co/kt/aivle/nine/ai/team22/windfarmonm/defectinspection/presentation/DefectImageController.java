package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.DefectQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto.DefectImageResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.Login;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 결함탐지 분석 결과 API. context-path(/api) 기준 /blades/{blade_id}/defect-images.
 * <p>
 * 미담당 사용자의 접근은 404 로 응답한다(존재 은닉). 썸네일은 담당 인가를 통과한 요청에만
 * presigned URL 로 발급된다(사진 열람 = 담당 기반 인가). 페이징은 두지 않는다(명세 합의).
 */
@RestController
@RequiredArgsConstructor
public class DefectImageController {

    private final DefectQueryService defectQueryService;

    /** 결함탐지 분석 결과 조회: GET /api/blades/{blade_id}/defect-images */
    @GetMapping("/blades/{bladeId}/defect-images")
    public ResponseEntity<ApiResponse<List<DefectImageResponse>>> getDefectImages(
            @PathVariable String bladeId,
            @Login LoginMember member) {
        List<DefectImageResponse> images = defectQueryService
                .getDefectImages(member.userId(), member.isAdmin(), ApiIds.toLong(bladeId)).stream()
                .map(DefectImageResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(images));
    }
}
