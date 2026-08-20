package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.event.InspectionUploadCompleted;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업로드 완료가 커밋된 뒤 썸네일 생성을 건다.
 * <p>
 * {@code AFTER_COMMIT} 인 이유: 커밋 전에 걸면 롤백된 점검의 썸네일을 만들게 된다.
 * 별도 빈으로 둔 이유: 같은 빈 안에서 부르면 자기호출이라 {@code @Async} 프록시를 타지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ThumbnailGenerationListener {

    private final ThumbnailService thumbnailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUploadCompleted(InspectionUploadCompleted event) {
        thumbnailService.generate(event.inspectionId());
    }
}
