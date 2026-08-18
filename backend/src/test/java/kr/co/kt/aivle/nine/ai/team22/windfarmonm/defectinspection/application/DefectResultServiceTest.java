package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.DefectReportPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Defect;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.DefectRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Inspection;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.InspectionRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.InspectionStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEventRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 추론 결과 통보 처리의 분기를 검증한다: 결함 적재(bbox 변환·severity 파싱), 멱등(중복 통보 skip),
 * 실패 통보(FAILED), 마지막 이미지 완료 시 INSPECTED 전이 + 보고서 생성 요청.
 */
@ExtendWith(MockitoExtension.class)
class DefectResultServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 8, 2, 0, 0);

    private static final String PAYLOAD = """
            {"inspection_id":5,"image_key":"content/inspections/5/31/LE/1.jpg","blade_id":31,"part_side":"LE"}""";

    @Mock
    OutboxEventRepository outboxEventRepository;
    @Mock
    InspectionRepository inspectionRepository;
    @Mock
    DefectRepository defectRepository;
    @Mock
    InspectionStoragePort storagePort;
    @Mock
    DefectReportPort reportPort;

    DefectResultService service;

    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        service = new DefectResultService(outboxEventRepository, inspectionRepository, defectRepository,
                storagePort, reportPort, JsonMapper.builder().build());
        event = OutboxEvent.pending("Inspection", "5", "InspectionImageUploaded", PAYLOAD);
        event.markPublished();
    }

    private String completedNotification() {
        return """
                {"invocationStatus":"Completed","inferenceId":"77",
                 "requestParameters":{"contentType":"image/jpeg","inputLocation":"s3://bucket/content/inspections/5/31/LE/1.jpg"},
                 "responseParameters":{"contentType":"application/json","outputLocation":"s3://bucket/async-out/abc.json"}}""";
    }

    @Test
    @DisplayName("Completed: 결함을 적재(bbox x1y1x2y2→xywh, severity 정수)하고 행을 COMPLETED 로 종결한다")
    void completed_loadsDefects() {
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));
        when(storagePort.readJson("s3://bucket/async-out/abc.json")).thenReturn("""
                [{"bbox":[10.0,20.0,50.0,80.0],"defect_type":"Paint Damage","confidence":0.97,"severity":"3"},
                 {"bbox":[1.0,2.0,3.0,4.0],"defect_type":"Crack","confidence":0.5,"severity":"high"}]""");
        // 종료 판정은 점검 행을 먼저 잠근 뒤 미완료를 조회한다 — 잠금 스텁이 없으면 그 경로에 닿지 않는다.
        Inspection inspecting = Inspection.request(7L, 10L, 90L, START, END);
        inspecting.markInspecting();
        when(inspectionRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(inspecting));
        when(outboxEventRepository.existsUnfinishedByAggregate("Inspection", "5")).thenReturn(true); // 아직 남음

        boolean done = service.process(completedNotification());

        assertThat(done).isTrue();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        ArgumentCaptor<List<Defect>> captor = ArgumentCaptor.captor();
        verify(defectRepository).saveAll(captor.capture());
        List<Defect> defects = captor.getValue();
        assertThat(defects).hasSize(2);
        Defect first = defects.getFirst();
        assertThat(first.getInspectionId()).isEqualTo(5L);
        assertThat(first.getBladeId()).isEqualTo(31L);
        assertThat(first.getDefectType()).isEqualTo("Paint Damage");
        assertThat(first.getSeverity()).isEqualTo(3);
        assertThat(first.getPartSide()).isEqualTo("LE");
        assertThat(first.getBboxX()).isEqualTo(10.0);
        assertThat(first.getBboxY()).isEqualTo(20.0);
        assertThat(first.getBboxW()).isEqualTo(40.0); // x2-x1
        assertThat(first.getBboxH()).isEqualTo(60.0); // y2-y1
        assertThat(first.getImagePath()).isEqualTo("content/inspections/5/31/LE/1.jpg");
        assertThat(defects.get(1).getSeverity()).isNull(); // 비숫자 클래스명 → null
        // 아직 미완 이미지가 남아 전이/생성은 없다
        assertThat(inspecting.getStatus()).isEqualTo(InspectionStatus.INSPECTING);
        verify(reportPort, never()).requestGeneration(any());
    }

    @Test
    @DisplayName("마지막 이미지 완료: INSPECTED 전이 + 보고서 생성 요청")
    void lastImage_finishesInspection() {
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));
        when(storagePort.readJson(any())).thenReturn("[]"); // 결함 0건도 정상 종결
        when(outboxEventRepository.existsUnfinishedByAggregate("Inspection", "5")).thenReturn(false);
        when(outboxEventRepository.existsCompletedByAggregate("Inspection", "5")).thenReturn(true);
        Inspection inspection = Inspection.request(7L, 10L, 90L, START, END);
        inspection.markInspecting();
        when(inspectionRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(inspection));

        service.process(completedNotification());

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.INSPECTED);
        verify(reportPort).requestGeneration(90L);
        verify(defectRepository, never()).saveAll(any()); // 0건이면 저장 호출 없음
    }

    @Test
    @DisplayName("잠금 대기 중 다른 소비자가 이미 종결했으면 조용히 물러난다(재전이 예외 방지)")
    void alreadyFinished_backsOff() {
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));
        when(storagePort.readJson(any())).thenReturn("[]");
        when(outboxEventRepository.existsUnfinishedByAggregate("Inspection", "5")).thenReturn(false);
        Inspection inspection = Inspection.request(7L, 10L, 90L, START, END);
        inspection.markInspecting();
        inspection.markInspected();   // 경쟁 소비자가 먼저 끝냈다
        when(inspectionRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(inspection));

        service.process(completedNotification());   // 예외가 새지 않아야 한다

        verify(reportPort, never()).requestGeneration(any());
    }

    @Test
    @DisplayName("중복 통보(이미 COMPLETED)는 아무것도 하지 않고 종결로 답한다(멱등)")
    void duplicate_skipped() {
        event.markCompleted();
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));

        assertThat(service.process(completedNotification())).isTrue();

        verify(storagePort, never()).readJson(any());
        verify(defectRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Failed 통보: 행을 FAILED 로 종결하고 결함은 적재하지 않는다")
    void failed_marksFailed() {
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));
        lenient().when(outboxEventRepository.existsUnfinishedByAggregate("Inspection", "5")).thenReturn(true);

        boolean done = service.process("""
                {"invocationStatus":"Failed","inferenceId":"77","failureReason":"model error"}""");

        assertThat(done).isTrue();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(defectRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("전부 실패한 세션: INSPECTED 로 닫되 보고서는 생성하지 않는다(없는 안전을 보고하지 않기 위해)")
    void allFailed_noReportGeneration() {
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));
        when(outboxEventRepository.existsUnfinishedByAggregate("Inspection", "5")).thenReturn(false);
        when(outboxEventRepository.existsCompletedByAggregate("Inspection", "5")).thenReturn(false);
        lenient().when(outboxEventRepository.countFailedByAggregate("Inspection", "5")).thenReturn(3L);
        Inspection inspection = Inspection.request(7L, 10L, 90L, START, END);
        inspection.markInspecting();
        when(inspectionRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(inspection));

        service.process("""
                {"invocationStatus":"Failed","inferenceId":"77","failureReason":"model error"}""");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.INSPECTED); // 영구 정체 방지
        verify(reportPort, never()).requestGeneration(any());                     // 빈 보고서 방지
    }

    @Test
    @DisplayName("일부만 실패한 세션: 확보된 결함으로 보고서를 생성한다(부분 결과가 무결과보다 낫다)")
    void partialFailure_stillGeneratesReport() {
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));
        when(outboxEventRepository.existsUnfinishedByAggregate("Inspection", "5")).thenReturn(false);
        when(outboxEventRepository.existsCompletedByAggregate("Inspection", "5")).thenReturn(true);
        when(outboxEventRepository.countFailedByAggregate("Inspection", "5")).thenReturn(1L);
        Inspection inspection = Inspection.request(7L, 10L, 90L, START, END);
        inspection.markInspecting();
        when(inspectionRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(inspection));

        service.process("""
                {"invocationStatus":"Failed","inferenceId":"77","failureReason":"model error"}""");

        assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.INSPECTED);
        verify(reportPort).requestGeneration(90L);
    }

    @Test
    @DisplayName("계약 범위(1~4) 밖 severity 는 null 로 적재한다 — max_severity 오염 방지")
    void severityOutOfRange_storedAsNull() {
        when(outboxEventRepository.findById(77L)).thenReturn(Optional.of(event));
        when(storagePort.readJson(any())).thenReturn("""
                [{"bbox":[1.0,2.0,3.0,4.0],"defect_type":"Crack","confidence":0.9,"severity":"9"},
                 {"bbox":[1.0,2.0,3.0,4.0],"defect_type":"Crack","confidence":0.9,"severity":"0"},
                 {"bbox":[1.0,2.0,3.0,4.0],"defect_type":"Crack","confidence":0.9,"severity":"4"}]""");
        lenient().when(outboxEventRepository.existsUnfinishedByAggregate("Inspection", "5")).thenReturn(true);

        service.process(completedNotification());

        ArgumentCaptor<List<Defect>> captor = ArgumentCaptor.captor();
        verify(defectRepository).saveAll(captor.capture());
        List<Defect> defects = captor.getValue();
        assertThat(defects.get(0).getSeverity()).isNull(); // 9 = 범위 초과
        assertThat(defects.get(1).getSeverity()).isNull(); // 0 = 범위 미만
        assertThat(defects.get(2).getSeverity()).isEqualTo(4);
    }

    @Test
    @DisplayName("inferenceId 미상/아웃박스 미존재 통보는 폐기(true) — 재배달해도 소용없다")
    void unknownNotifications_discarded() {
        assertThat(service.process("{\"invocationStatus\":\"Completed\"}")).isTrue();

        when(outboxEventRepository.findById(999L)).thenReturn(Optional.empty());
        assertThat(service.process("{\"inferenceId\":\"999\",\"invocationStatus\":\"Completed\"}")).isTrue();

        verify(defectRepository, never()).saveAll(any());
    }
}
