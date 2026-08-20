package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation;

import jakarta.validation.Valid;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.InspectionCommandService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.InspectionQueryService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.CreateInspectionResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto.CompleteUploadRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto.CreateInspectionRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto.CreateInspectionResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto.InspectionImagesResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.Login;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final InspectionQueryService inspectionQueryService;

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
     * <p>
     * 본문은 선택이다. {@code {"uploaded_count": N}} 을 주면 S3 실측과 대조해 다르면 400(D006)으로
     * 거부한다 — 일부 PUT 실패를 조용히 넘기면 누락분이 영원히 추론되지 않는다. 본문이 없으면
     * 존재 확인만 한다(기존 호출과 동일).
     */
    @PostMapping("/{inspectionId}/images-uploaded")
    public ResponseEntity<ApiResponse<Void>> imagesUploaded(
            @PathVariable String inspectionId,
            @RequestBody(required = false) CompleteUploadRequest request,
            @Login LoginMember member) {
        inspectionCommandService.completeUpload(member.userId(), member.isAdmin(), ApiIds.toLong(inspectionId),
                CompleteUploadRequest.expectedCountOf(request));
        return ResponseEntity.ok(ApiResponse.success("업로드 완료를 접수했습니다.", null));
    }

    /**
     * 썸네일 생성 요청: POST /api/inspections/{inspection_id}/thumbnails
     * <p>
     * 업로드 완료 통보를 받으면 자동으로 돌지만, ① 그 기능이 없던 시절의 <b>기존 점검</b>과
     * ② 재배포로 대기 큐가 날아간 경우를 위한 재실행 창구다. 이미 있는 썸네일은 건너뛰므로
     * 몇 번을 불러도 안전하다(멱등).
     * <p>
     * 생성은 비동기라 접수만 하고 202 로 답한다 — 80장이면 수십 초가 걸려 동기로는 응답이 끊긴다.
     * 완료 여부는 결함 이미지 조회의 {@code thumbnail_url} 이 썸네일 키로 바뀌는 것으로 확인한다.
     */
    @PostMapping("/{inspectionId}/thumbnails")
    public ResponseEntity<ApiResponse<Void>> generateThumbnails(
            @PathVariable String inspectionId,
            @Login LoginMember member) {
        inspectionCommandService.requestThumbnailGeneration(
                member.userId(), member.isAdmin(), ApiIds.toLong(inspectionId));
        return ResponseEntity.accepted().body(ApiResponse.success("썸네일 생성을 시작했습니다.", null));
    }

    /**
     * 업로드된 이미지 조회: GET /api/inspections/{inspection_id}/images
     * <p>
     * <b>S3 원천 기준</b>이라 추론 전에도 실제 업로드 결과가 그대로 보인다 — FE 가 업로드 성공을
     * 확인하는 용도다. 결함 이미지 조회({@code /blades/{id}/defect-images})는 결함이 검출된 것만
     * 나오고 추론 완료 후에야 채워지므로 이 목적에는 쓸 수 없다.
     * <p>
     * 미담당/미존재 점검은 404 로 은닉된다. 썸네일이 없어 원본 presigned URL 을 주므로, FE 는
     * 지연 로딩(loading="lazy")으로 목록을 그릴 것(원본은 장당 수 MB 다).
     */
    @GetMapping("/{inspectionId}/images")
    public ResponseEntity<ApiResponse<InspectionImagesResponse>> getUploadedImages(
            @PathVariable String inspectionId,
            @Login LoginMember member) {
        return ResponseEntity.ok(ApiResponse.success(InspectionImagesResponse.from(
                inspectionQueryService.getUploadedImages(
                        member.userId(), member.isAdmin(), ApiIds.toLong(inspectionId)))));
    }
}
