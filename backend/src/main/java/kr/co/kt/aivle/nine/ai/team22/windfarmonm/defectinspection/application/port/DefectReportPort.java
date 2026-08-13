package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port;

import java.time.LocalDateTime;

/**
 * 결함 진단 보고서 생성 포트(maintenancereporting 위임). 소비자(defectinspection)가 소유한다.
 * 점검 세션 생성 트랜잭션에 참여해 <b>세션당 1건</b>의 보고서 행(PENDING)을 만든다 —
 * 본문 생성 트리거는 결함 적재 완료(P5)가 담당.
 */
public interface DefectReportPort {

    /**
     * 결함 진단 보고서 행을 만들고 id 를 돌려준다. 호출측 인가 선행을 전제한다.
     * 세션이 터빈 여러 대를 묶으므로 특정 터빈을 지정하지 않는다(turbine_id null).
     *
     * @param context 사용자가 넣은 보고서 참고사항(초기 본문). null 허용
     */
    Long createDefectReport(Long windFarmId, LocalDateTime periodStart, LocalDateTime periodEnd,
                            Long createdBy, String context);
}
