package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation;

import jakarta.validation.Valid;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.InspectionCommandService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.CreateInspectionResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto.CreateInspectionRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto.CreateInspectionResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.Login;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 점검(결함탐지) API. context-path(/api) 기준 /inspections.
 * <p>
 * 미담당 사용자의 접근은 404 로 응답한다(존재 은닉 — 보고서/알림과 동일 규약).
 */
@RestController
@RequestMapping("/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionCommandService inspectionCommandService;

    /**
     * 드론 점검 세션 생성: POST /api/inspections
     * <p>
     * 터빈마다 점검 행, 세션당 결함 보고서 행(빈 상태)을 만들고 블레이드·부위별 presigned PUT URL 을
     * 반환한다(200 — 명세). S3 미설정 환경이면 503(S001).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateInspectionResponse>> createInspection(
            @Valid @RequestBody CreateInspectionRequest request,
            @Login LoginMember member) {
        CreateInspectionResult result =
                inspectionCommandService.create(member.userId(), member.isAdmin(), request.toCommand());
        return ResponseEntity.ok(
                ApiResponse.success("점검을 등록했습니다.", CreateInspectionResponse.from(result)));
    }

    /**
     * 드론 이미지 업로드 완료 통보: POST /api/inspections/{inspection_id}/images-uploaded
     * <p>
     * 상태를 INSPECTING 으로 전이하고, 실제 업로드된 이미지(S3 기준)마다 추론 요청을 아웃박스에 기록한다.
     * 이미 완료된 점검이면 400(D002 — 명세).
     */
    @PostMapping("/{inspectionId}/images-uploaded")
    public ResponseEntity<ApiResponse<Void>> imagesUploaded(
            @PathVariable String inspectionId,
            @Login LoginMember member) {
        inspectionCommandService.completeUpload(member.userId(), member.isAdmin(), ApiIds.toLong(inspectionId));
        return ResponseEntity.ok(ApiResponse.success("업로드 완료를 접수했습니다.", null));
    }
}
