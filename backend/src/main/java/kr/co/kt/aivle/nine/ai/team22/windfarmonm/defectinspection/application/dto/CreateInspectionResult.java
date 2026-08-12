package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto;

import java.util.List;

/**
 * 드론 점검 세션 생성 결과. 명세 응답 구조를 그대로 반영한다 —
 * 터빈별 inspection_id 와 블레이드별 부위(LE/PS/SS/TE) presigned 업로드 URL 목록, 세션 공유 report_id.
 */
public record CreateInspectionResult(Long windFarmId, List<TurbineResult> turbines, Long reportId) {

    public record TurbineResult(Long turbineId, Long inspectionId, List<BladeResult> blades) {
    }

    /** 부위별 업로드 URL(요청 count 만큼, count 0 이면 빈 목록). */
    public record BladeResult(Long bladeId,
                              List<String> leadingEdgeUploadUrls, List<String> pressureSideUploadUrls,
                              List<String> suctionSideUploadUrls, List<String> trailingEdgeUploadUrls) {
    }
}
