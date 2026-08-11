package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.DefectResultService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.SqsQueueClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;

/**
 * 추론 결과 큐(windfarm-sqs) 폴러. long-polling(10초 대기)이라 체감 지연은 푸시급이면서
 * 인바운드 웹훅 없이 아웃바운드만으로 결과를 소비한다.
 * <p>
 * ShedLock 으로 <b>단일 인스턴스·단일 스레드 순차 소비</b>를 보장한다 — {@link DefectResultService} 의
 * "마지막 이미지 완료 → INSPECTED" 판정이 이 전제 위에 있다(병렬 소비로 바꾸면 그 판정부터 재설계).
 * 처리 성공(종결)한 메시지만 삭제한다. 예외가 난 메시지는 남겨 visibility timeout 후 재배달되고,
 * 반복 실패는 큐의 redrive 정책이 windfarm-dlq 로 옮긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InferenceResultPoller {

    private final SqsQueueClient sqsQueueClient;
    private final DefectResultService resultService;

    @Scheduled(fixedDelayString = "PT2S")
    @SchedulerLock(name = "defect-result-poller", lockAtMostFor = "PT2M")
    public void poll() {
        if (!sqsQueueClient.isConfigured()) {
            return; // 큐 미설정 — 휴면(CI/로컬에서 무해)
        }
        List<Message> messages = sqsQueueClient.receiveResultMessages();
        for (Message message : messages) {
            try {
                if (resultService.process(message.body())) { // tx — 결함 적재 + 상태 전이
                    sqsQueueClient.deleteResultMessage(message.receiptHandle());
                }
            } catch (RuntimeException e) {
                // 미삭제 → 재배달(일시 오류 재시도). 영구 오류는 process 가 true 로 종결하므로 여기 오지 않는다.
                log.warn("추론 결과 메시지 처리 실패 — 재배달 대기(messageId={})", message.messageId(), e);
            }
        }
    }
}
