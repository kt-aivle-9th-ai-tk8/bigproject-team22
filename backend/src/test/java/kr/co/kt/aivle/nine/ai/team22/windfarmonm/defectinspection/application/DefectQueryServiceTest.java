package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.DefectImageResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionAssetPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Defect;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.DefectRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.PartSide;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결함 이미지 조회의 분기를 검증한다: 이미지 단위 그룹핑, max_severity(null 무시), 담당 인가(404 은닉),
 * ADMIN 미존재 404, 인가 통과 후에만 presigned 발급.
 */
@ExtendWith(MockitoExtension.class)
class DefectQueryServiceTest {

    private static final long USER_ID = 10L;
    private static final long BLADE_ID = 31L;

    @Mock
    DefectRepository defectRepository;
    @Mock
    InspectionAssetPort assetPort;
    @Mock
    InspectionStoragePort storagePort;
    @InjectMocks
    DefectQueryService service;

    private Defect defect(String imageKey, String type, Integer severity) {
        return Defect.detected(5L, BLADE_ID, type, severity, PartSide.LE,
                1.0, 2.0, 3.0, 4.0, 0.9, imageKey);
    }

    @Test
    @DisplayName("이미지(image_path) 단위로 그룹핑하고 max_severity 는 null 을 무시한 최댓값이다")
    void groupsByImage() {
        when(defectRepository.findByBladeId(BLADE_ID)).thenReturn(List.of(
                defect("img/1.jpg", "Crack", 2),
                defect("img/1.jpg", "Paint Damage", null),
                defect("img/1.jpg", "Erosion", 4),
                defect("img/2.jpg", "Contamination", null)));
        when(storagePort.presignImageView(any())).thenReturn("https://presigned");

        List<DefectImageResult> results = service.getDefectImages(USER_ID, false, BLADE_ID);

        assertThat(results).hasSize(2);
        DefectImageResult first = results.getFirst();
        assertThat(first.imagePath()).isEqualTo("img/1.jpg");
        assertThat(first.defects()).hasSize(3);
        assertThat(first.maxSeverity()).isEqualTo(4);
        assertThat(first.partSide()).isEqualTo("LE");
        assertThat(first.thumbnailUrl()).isEqualTo("https://presigned");
        assertThat(results.get(1).maxSeverity()).isNull(); // 전부 null 이면 null
    }

    @Test
    @DisplayName("비담당 사용자는 404 로 은닉되고 presigned 는 발급되지 않는다")
    void unauthorized_hidden() {
        doThrow(new BusinessException(ErrorCode.BLADE_NOT_FOUND))
                .when(assetPort).checkBladeAccess(USER_ID, false, BLADE_ID);

        assertThatThrownBy(() -> service.getDefectImages(USER_ID, false, BLADE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLADE_NOT_FOUND);
        verify(storagePort, never()).presignImageView(any());
    }

    @Test
    @DisplayName("ADMIN 은 가드를 통과하므로 미존재 블레이드를 따로 404 로 판정한다")
    void admin_missingBlade_404() {
        when(assetPort.bladeExists(BLADE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getDefectImages(USER_ID, true, BLADE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLADE_NOT_FOUND);
    }
}
