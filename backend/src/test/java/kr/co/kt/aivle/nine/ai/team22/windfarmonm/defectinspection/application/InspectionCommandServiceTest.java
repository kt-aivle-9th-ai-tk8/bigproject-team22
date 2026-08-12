package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.CreateInspectionCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.CreateInspectionResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.DefectReportPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionAssetPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Inspection;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.InspectionRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEventRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.PartSide;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 점검 세션 생성/업로드 완료의 분기를 검증한다: 터빈별 점검·세션 공유 보고서, 부위별 presign 수량,
 * 소속 검증(404/D003), 존재 은닉(404), 상태 충돌(D002), 아웃박스 기록 내용.
 */
@ExtendWith(MockitoExtension.class)
class InspectionCommandServiceTest {

    private static final long USER_ID = 10L;
    private static final long FARM_ID = 2L;
    private static final long TURBINE_ID = 7L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 8, 2, 0, 0);

    @Mock
    InspectionRepository inspectionRepository;
    @Mock
    OutboxEventRepository outboxEventRepository;
    @Mock
    InspectionAssetPort assetPort;
    @Mock
    DefectReportPort reportPort;
    @Mock
    InspectionStoragePort storagePort;

    InspectionCommandService service;

    @BeforeEach
    void setUp() {
        // 운영과 동일한 snake_case 직렬화로 payload 계약을 검증한다.
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        service = new InspectionCommandService(
                inspectionRepository, outboxEventRepository, assetPort, reportPort, storagePort, mapper);
    }

    private CreateInspectionCommand.BladeSpec blade(long bladeId, int le, int ps, int ss, int te) {
        return new CreateInspectionCommand.BladeSpec(bladeId, le, ps, ss, te);
    }

    private void stubHappyCreate() {
        when(assetPort.windFarmIdOf(TURBINE_ID)).thenReturn(FARM_ID);
        when(assetPort.bladesOf(TURBINE_ID)).thenReturn(List.of(
                new InspectionAssetPort.BladeRef(31L, "A"),
                new InspectionAssetPort.BladeRef(32L, "B")));
        when(reportPort.createDefectReport(any(), any(), any(), any(), any())).thenReturn(90L);
        AtomicLong nextId = new AtomicLong(55);
        when(inspectionRepository.save(any())).thenAnswer(invocation -> {
            Inspection saved = invocation.getArgument(0);
            // 실제 저장소는 IDENTITY 로 id 를 채워 돌려준다 — 목에서도 동일하게 흉내낸다(presign 이 id 를 쓴다).
            ReflectionTestUtils.setField(saved, "id", nextId.getAndIncrement());
            return saved;
        });
        lenient().when(storagePort.presignImageUpload(anyLong(), anyLong(), any(), anyInt()))
                .thenAnswer(inv -> new InspectionStoragePort.UploadTarget(
                        "key-%d-%s-%d".formatted((long) inv.getArgument(1), inv.getArgument(2), (int) inv.getArgument(3)),
                        "https://presigned/%s/%d".formatted(inv.getArgument(2), (int) inv.getArgument(3))));
    }

    @Test
    @DisplayName("생성: 부위별 count 만큼 URL 을 발급하고, 세션 보고서 1건 + 터빈별 점검 행을 만든다")
    void create_sessionWithPresignedUrls() {
        stubHappyCreate();

        CreateInspectionResult result = service.create(USER_ID, false, new CreateInspectionCommand(
                FARM_ID, START, END,
                List.of(new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(
                        blade(31L, 2, 1, 0, 0),
                        blade(32L, 0, 0, 1, 3)))),
                "참고사항"));

        assertThat(result.reportId()).isEqualTo(90L);
        assertThat(result.turbines()).hasSize(1);
        CreateInspectionResult.TurbineResult turbine = result.turbines().getFirst();
        assertThat(turbine.inspectionId()).isEqualTo(55L);
        CreateInspectionResult.BladeResult bladeA = turbine.blades().getFirst();
        assertThat(bladeA.leadingEdgeUploadUrls()).hasSize(2);
        assertThat(bladeA.pressureSideUploadUrls()).hasSize(1);
        assertThat(bladeA.suctionSideUploadUrls()).isEmpty(); // count 0 → 빈 목록
        CreateInspectionResult.BladeResult bladeB = turbine.blades().get(1);
        assertThat(bladeB.trailingEdgeUploadUrls()).hasSize(3);
        verify(reportPort).createDefectReport(eq(FARM_ID), any(), any(), eq(USER_ID), eq("참고사항"));
        verify(inspectionRepository).save(any(Inspection.class));
    }

    @Test
    @DisplayName("생성: 터빈이 요청 단지 소속이 아니면 404(명세: turbine_id not found)")
    void create_turbineNotInFarm_404() {
        when(assetPort.windFarmIdOf(TURBINE_ID)).thenReturn(99L); // 타 단지 소속

        assertThatThrownBy(() -> service.create(USER_ID, false, new CreateInspectionCommand(
                FARM_ID, START, END,
                List.of(new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(blade(31L, 1, 0, 0, 0)))),
                null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TURBINE_NOT_FOUND);
        verify(reportPort, never()).createDefectReport(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("생성: 그 터빈에 없는 블레이드 id 면 400 D003(잘못된 대상)")
    void create_unknownBlade_rejected() {
        when(assetPort.windFarmIdOf(TURBINE_ID)).thenReturn(FARM_ID);
        when(assetPort.bladesOf(TURBINE_ID)).thenReturn(List.of(
                new InspectionAssetPort.BladeRef(31L, "A")));
        when(reportPort.createDefectReport(any(), any(), any(), any(), any())).thenReturn(90L);

        assertThatThrownBy(() -> service.create(USER_ID, false, new CreateInspectionCommand(
                FARM_ID, START, END,
                List.of(new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(blade(999L, 1, 0, 0, 0)))),
                null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INSPECTION_TARGET);
    }

    @Test
    @DisplayName("생성: 터빈 0대/중복 터빈/음수 count/이미지 0장/상한 초과는 400")
    void create_invalidInput_rejected() {
        List<CreateInspectionCommand> invalids = List.of(
                new CreateInspectionCommand(FARM_ID, START, END, List.of(), null),
                new CreateInspectionCommand(FARM_ID, START, END, List.of(
                        new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(blade(31L, 1, 0, 0, 0))),
                        new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(blade(32L, 1, 0, 0, 0)))), null),
                new CreateInspectionCommand(FARM_ID, START, END, List.of(
                        new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(blade(31L, -1, 0, 0, 0)))), null),
                new CreateInspectionCommand(FARM_ID, START, END, List.of(
                        new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(blade(31L, 0, 0, 0, 0)))), null),
                new CreateInspectionCommand(FARM_ID, START, END, List.of(
                        new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(
                                blade(31L, 1, 0, 0, 0), blade(31L, 2, 0, 0, 0)))), null), // 중복 블레이드 = 키 충돌 → 거부
                new CreateInspectionCommand(FARM_ID, START, END, List.of(
                        new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(
                                blade(31L, InspectionCommandService.MAX_IMAGES_PER_SIDE + 1, 0, 0, 0)))), null), // 부위별 상한(20) 초과
                new CreateInspectionCommand(FARM_ID, START, END, List.of(
                        new CreateInspectionCommand.TurbineSpec(TURBINE_ID, List.of(
                                blade(31L, 20, 20, 20, 20),
                                blade(32L, 20, 20, 20, 20),
                                blade(33L, 20, 20, 20, 20))))
                        , null)); // 부위별은 통과해도 총합(240>200) 초과

        for (CreateInspectionCommand command : invalids) {
            assertThatThrownBy(() -> service.create(USER_ID, false, command))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Test
    @DisplayName("완료: 업로드된 이미지마다 아웃박스 행을 기록한다(payload 는 snake_case 계약)")
    void completeUpload_recordsOutboxPerImage() {
        Inspection inspection = Inspection.request(TURBINE_ID, USER_ID, 90L, START, END);
        when(inspectionRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(inspection));
        when(storagePort.listUploadedImages(5L)).thenReturn(List.of(
                new InspectionStoragePort.UploadedImage("content/inspections/5/31/LE/1.jpg", 31L, PartSide.LE),
                new InspectionStoragePort.UploadedImage("content/inspections/5/31/LE/2.jpg", 31L, PartSide.LE)));

        int count = service.completeUpload(USER_ID, false, 5L);

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<List<OutboxEvent>> captor = ArgumentCaptor.captor();
        verify(outboxEventRepository).saveAll(captor.capture());
        List<OutboxEvent> events = captor.getValue();
        assertThat(events).hasSize(2);
        OutboxEvent first = events.getFirst();
        assertThat(first.getAggregateType()).isEqualTo("Inspection");
        assertThat(first.getAggregateId()).isEqualTo("5");
        assertThat(first.getEventType()).isEqualTo("InspectionImageUploaded");
        assertThat(first.getPayload())
                .contains("\"inspection_id\":5")
                .contains("\"image_key\":\"content/inspections/5/31/LE/1.jpg\"")
                .contains("\"blade_id\":31")
                .contains("\"part_side\":\"LE\"");
    }

    @Test
    @DisplayName("완료: 미존재/비소유자 점검은 모두 404 D001(존재 은닉) — 완료 통보는 소유자 전용, ADMIN 도 예외 없음")
    void completeUpload_hiddenAs404() {
        when(inspectionRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.completeUpload(USER_ID, false, 404L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSPECTION_NOT_FOUND);

        // 비소유자는 접근(담당) 검사 이전에 소유자 검사에서 은닉된다 — 동일 단지 담당자여도 마찬가지
        Inspection othersInspection = Inspection.request(TURBINE_ID, 99L, 90L, START, END);
        when(inspectionRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(othersInspection));
        assertThatThrownBy(() -> service.completeUpload(USER_ID, false, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSPECTION_NOT_FOUND);
        assertThatThrownBy(() -> service.completeUpload(USER_ID, true, 5L)) // ADMIN 도 예외 없음
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSPECTION_NOT_FOUND);

        // 소유자라도 배정이 해제됐으면(담당 아님) 동일하게 은닉된다
        Inspection ownButRevoked = Inspection.request(TURBINE_ID, USER_ID, 90L, START, END);
        when(inspectionRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(ownButRevoked));
        doThrow(new BusinessException(ErrorCode.TURBINE_NOT_FOUND))
                .when(assetPort).checkTurbineAccess(USER_ID, false, TURBINE_ID);
        assertThatThrownBy(() -> service.completeUpload(USER_ID, false, 6L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSPECTION_NOT_FOUND); // M003 이 아니라 D001 로 은닉
    }

    @Test
    @DisplayName("완료: 중복 통보는 D002(명세상 400), 업로드 0건 통보는 400")
    void completeUpload_conflictAndEmpty() {
        Inspection inspection = Inspection.request(TURBINE_ID, USER_ID, 90L, START, END);
        inspection.markInspecting(); // 이미 완료 통보됨
        when(inspectionRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(inspection));
        assertThatThrownBy(() -> service.completeUpload(USER_ID, false, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSPECTION_STATE_CONFLICT);

        Inspection fresh = Inspection.request(TURBINE_ID, USER_ID, 90L, START, END);
        when(inspectionRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(fresh));
        when(storagePort.listUploadedImages(6L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.completeUpload(USER_ID, false, 6L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(outboxEventRepository, never()).saveAll(any());
    }
}
