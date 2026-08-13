package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 드론 점검 세션 생성 명령(명세: POST /api/inspections + 촬영 기간은 명세 누락분으로 합의 구현).
 * 한 세션 = 단지 1곳의 터빈 여러 대이며, 터빈마다 점검(inspection) 1행이 생기고 세션 전체가
 * 결함 보고서 1건을 공유한다. 촬영 기간은 세션의 모든 점검·보고서에 동일하게 적용된다.
 *
 * @param inspectionStart 드론 촬영 시작(inspection.inspection_start — RDS 컬럼명 그대로)
 * @param inspectionEnd   드론 촬영 종료(inspection.inspection_end)
 * @param context         보고서 참고사항(선택). 결함 보고서의 초기 본문으로 저장된다.
 */
public record CreateInspectionCommand(Long windFarmId,
                                      LocalDateTime inspectionStart, LocalDateTime inspectionEnd,
                                      List<TurbineSpec> turbines, String context) {

    /** 터빈 1대의 점검 명세. */
    public record TurbineSpec(Long turbineId, List<BladeSpec> blades) {
    }

    /** 블레이드 1개의 부위별 업로드 예정 이미지 수(LE/PS/SS/TE — 명세의 4개 필드). */
    public record BladeSpec(Long bladeId, int leadingEdgeCount, int pressureSideCount,
                            int suctionSideCount, int trailingEdgeCount) {

        /** 합계는 long — int 합산은 큰 입력에서 오버플로우해 상한 검사를 우회할 수 있다. */
        public long total() {
            return (long) leadingEdgeCount + pressureSideCount + suctionSideCount + trailingEdgeCount;
        }
    }
}
