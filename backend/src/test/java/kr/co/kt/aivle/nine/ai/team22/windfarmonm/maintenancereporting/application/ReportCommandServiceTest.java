package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.CreateReportCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportAssetPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 생성 로직 중 <b>앱이 판정하는 부분</b>만 단위로 검증한다: 유형·기간·대상 스코프·담당 인가.
 * <p>
 * 대상의 존재/소속 정합성은 이제 DB 제약(FK·복합 FK)이 강제하므로 여기서 mock 으로 흉내 낼 수 없다 —
 * 그 검증은 {@code ReportApiIntegrationTest}(실제 DB)로 옮겼다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 검증 실패로 조기 반환하는 케이스가 많아 스텁이 남는다
class ReportCommandServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 31, 23, 0);
    private static final Long USER_ID = 10L;

    @Mock
    ReportRepository reportRepository;
    @Mock
    ReportQueryService reportQueryService;
    @Mock
    ReportAssetPort assetPort;
    @InjectMocks
    ReportCommandService service;

    private static CreateReportCommand command(ReportType type, Long turbineId,
                                               LocalDateTime start, LocalDateTime end) {
        return new CreateReportCommand(type, 1L, turbineId, start, end, null);
    }

    /** 담당 단지 1 을 가진 일반 사용자로 가정. */
    private void assignedToFarm1() {
        when(assetPort.viewableWindFarmIds(USER_ID, false)).thenReturn(List.of(1L));
    }

    private static ErrorCode errorCodeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Test
    @DisplayName("사용자 요청 불가 유형(결함/이상)은 INVALID_REPORT_TYPE 로 거부한다")
    void create_rejectsNonUserRequestableType() {
        assignedToFarm1();

        for (ReportType type : List.of(ReportType.DEFECT_DIAGNOSIS, ReportType.ANOMALY_EVENT)) {
            assertThatThrownBy(() -> service.create(USER_ID, false, command(type, 2L, START, END)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ReportCommandServiceTest::errorCodeOf)
                    .isEqualTo(ErrorCode.INVALID_REPORT_TYPE);
        }
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("기간이 역전되면 INVALID_REPORT_PERIOD")
    void create_reversedPeriod() {
        assignedToFarm1();

        assertThatThrownBy(() -> service.create(USER_ID, false, command(ReportType.TURBINE_OPERATION, 2L, END, START)))
                .isInstanceOf(BusinessException.class)
                .extracting(ReportCommandServiceTest::errorCodeOf)
                .isEqualTo(ErrorCode.INVALID_REPORT_PERIOD);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("터빈 보고서인데 터빈이 없으면 INVALID_INPUT")
    void create_turbineReportWithoutTurbine() {
        assignedToFarm1();

        assertThatThrownBy(() -> service.create(USER_ID, false, command(ReportType.TURBINE_OPERATION, null, START, END)))
                .isInstanceOf(BusinessException.class)
                .extracting(ReportCommandServiceTest::errorCodeOf)
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("단지 보고서인데 터빈이 지정되면 INVALID_INPUT")
    void create_windFarmReportWithTurbine() {
        assignedToFarm1();

        assertThatThrownBy(() -> service.create(USER_ID, false, command(ReportType.WIND_FARM_OPERATION, 2L, START, END)))
                .isInstanceOf(BusinessException.class)
                .extracting(ReportCommandServiceTest::errorCodeOf)
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("담당이 아닌 단지는 접근거부가 아니라 WIND_FARM_NOT_FOUND(존재 은닉)")
    void create_notAssignedWindFarm_hidesExistence() {
        when(assetPort.viewableWindFarmIds(USER_ID, false)).thenReturn(List.of(99L)); // 1L 은 담당 아님

        assertThatThrownBy(() -> service.create(USER_ID, false, command(ReportType.TURBINE_OPERATION, 2L, START, END)))
                .isInstanceOf(BusinessException.class)
                .extracting(ReportCommandServiceTest::errorCodeOf)
                .isEqualTo(ErrorCode.WIND_FARM_NOT_FOUND);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN 은 담당 제한 없이 생성한다(존재/소속은 저장 시 DB 가 검증)")
    void create_adminHasNoScopeRestriction() {
        when(assetPort.viewableWindFarmIds(USER_ID, true)).thenReturn(null); // ADMIN = 제한 없음
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(USER_ID, true, command(ReportType.TURBINE_OPERATION, 2L, START, END));

        verify(reportRepository).save(any(Report.class));
    }
}
