package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.UploadedImageResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionAssetPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Inspection;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.InspectionRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 점검 조회. 업로드된 이미지를 <b>S3 원천 기준</b>으로 돌려준다 — DB(defect)가 아니라 저장소를 보므로
 * 추론 전에도 "무엇이 실제로 올라갔는지"가 그대로 드러난다.
 * <p>
 * 결함 이미지 조회({@code /blades/{id}/defect-images})와 용도가 다르다: 그쪽은 <b>결함이 검출된</b>
 * 이미지만 나오고 추론이 끝나야 채워진다. 이쪽은 업로드 검증·정상 이미지 확인에 쓴다.
 */
@Service
@RequiredArgsConstructor
public class InspectionQueryService {

    private final InspectionRepository inspectionRepository;
    private final InspectionAssetPort assetPort;
    private final InspectionStoragePort storagePort;

    /**
     * 점검에 업로드된 이미지 목록. 미담당/미존재는 404 로 은닉된다(가드 규약).
     * <p>
     * 열람 인가는 <b>담당 단지 기준</b>이다 — 상태를 바꾸는 완료 통보(소유자 전용)와 달리, 읽기는
     * 보고서·결함 조회와 같은 입자를 쓴다(같은 단지 담당자는 서로의 점검 결과를 본다).
     */
    @Transactional(readOnly = true)
    public List<UploadedImageResult> getUploadedImages(Long userId, boolean admin, Long inspectionId) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSPECTION_NOT_FOUND));
        try {
            assetPort.checkTurbineAccess(userId, admin, inspection.getTurbineId());
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.INSPECTION_NOT_FOUND); // 타인 점검은 존재 은닉
        }
        return storagePort.listUploadedImages(inspectionId).stream()
                .map(image -> new UploadedImageResult(
                        image.key(),
                        storagePort.presignImageView(image.key()),
                        image.bladeId(),
                        image.partSide().name()))
                .toList();
    }
}
