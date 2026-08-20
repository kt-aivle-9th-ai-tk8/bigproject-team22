package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 표시용 썸네일 생성. 원본(장당 약 10MB)을 목록에 그대로 내보내지 않기 위한 파생물이다.
 * <p>
 * <b>왜 애플리케이션이 만드는가</b>: 원래는 S3 이벤트 → Lambda(이슈 #131 의 D 안)로 정했으나 당장
 * 붙이기 어려워, 차선인 C 안(BE 처리)으로 간다. 서브샘플링 디코딩 덕에 장당 메모리가 약 11MB 라
 * 애플리케이션에서 감당된다.
 * <p>
 * <b>비동기 + 단일 스레드</b>다. 업로드 완료 응답을 붙잡으면 안 되고(80장이면 수십 초), 동시에 여러
 * 점검을 갈면 힙이 위험하므로 한 장씩 순차 처리한다.
 * <p>
 * 유실 가능성을 인정한다 — 큐가 메모리에 있어 재시작 시 대기분이 사라진다. 그래서 점검 단위 재실행
 * 엔드포인트를 두었고, 생성은 멱등이라(이미 있으면 건너뜀) 몇 번을 다시 돌려도 안전하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final InspectionStoragePort storagePort;

    /**
     * 점검 1건의 썸네일 중 <b>없는 것만</b> 만든다. 호출측 스레드를 붙잡지 않는다.
     * <p>
     * 한 장이 실패해도 나머지를 계속한다(손상 파일 하나가 세션 전체를 막지 않도록). 저장소 미설정 등으로
     * 통째로 실패하면 로그만 남긴다 — 썸네일은 표시 편의이지 점검의 진행 조건이 아니다.
     */
    @Async("thumbnailExecutor")
    public void generate(Long inspectionId) {
        try {
            int created = storagePort.createMissingThumbnails(inspectionId);
            if (created > 0) {
                log.info("점검 {} 썸네일 {}장 생성", inspectionId, created);
            }
        } catch (RuntimeException e) {
            log.warn("점검 {} 썸네일 생성 실패 — 원본으로 폴백되어 화면은 뜬다", inspectionId, e);
        }
    }
}
