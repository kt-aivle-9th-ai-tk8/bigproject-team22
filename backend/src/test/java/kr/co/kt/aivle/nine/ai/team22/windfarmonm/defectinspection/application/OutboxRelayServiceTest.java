package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InferenceDispatchPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEventRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 릴레이 발사 조각을 검증한다: 접수(inferenceId=행 id) + PUBLISHED 전이, 규약 밖 payload 의 FAILED 처리.
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    OutboxEventRepository outboxEventRepository;
    @Mock
    InferenceDispatchPort dispatchPort;
    @Mock
    InspectionStoragePort storagePort;

    OutboxRelayService service;

    @BeforeEach
    void setUp() {
        service = new OutboxRelayService(outboxEventRepository, dispatchPort, storagePort,
                JsonMapper.builder().build());
    }

    private OutboxEvent pendingEvent(long id, String payload) {
        OutboxEvent event = OutboxEvent.pending("Inspection", "5", "InspectionImageUploaded", payload);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    @Test
    @DisplayName("발사: 이미지 S3 URI 와 inferenceId(행 id)로 접수하고 PUBLISHED 로 전이한다")
    void publishOne_dispatches() {
        OutboxEvent event = pendingEvent(77L,
                "{\"inspection_id\":5,\"image_key\":\"content/inspections/5/31/LE/1.jpg\",\"blade_id\":31,\"part_side\":\"LE\"}");
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));
        when(storagePort.imageS3Uri("content/inspections/5/31/LE/1.jpg"))
                .thenReturn("s3://bucket/content/inspections/5/31/LE/1.jpg");

        service.publishOne(77L);

        verify(dispatchPort).dispatch("s3://bucket/content/inspections/5/31/LE/1.jpg", "77");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("PENDING 이 아닌 행(경쟁 처리됨)은 건너뛴다")
    void publishOne_skipsNonPending() {
        OutboxEvent event = pendingEvent(77L, "{}");
        event.markPublished();
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));

        service.publishOne(77L);

        verify(dispatchPort, never()).dispatch(any(), any());
    }

    @Test
    @DisplayName("payload 가 깨진 JSON 이어도 FAILED 로 종결한다 — 예외를 올리면 PENDING 무한 재시도가 된다")
    void publishOne_malformedJson_failed() {
        OutboxEvent event = pendingEvent(77L, "{not-json");
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));

        service.publishOne(77L);   // 예외가 새지 않아야 한다

        verify(dispatchPort, never()).dispatch(any(), any());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("payload 에 image_key 가 없으면 FAILED 로 빼서 무한 재시도를 막는다")
    void publishOne_invalidPayload_failed() {
        OutboxEvent event = pendingEvent(77L, "{\"inspection_id\":5}");
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));

        service.publishOne(77L);

        verify(dispatchPort, never()).dispatch(any(), any());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
