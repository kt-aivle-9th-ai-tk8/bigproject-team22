package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.DefectImageResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionAssetPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Defect;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.DefectRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 결함탐지 분석 결과 조회(이미지 단위 그룹핑). 사진 열람은 담당 기반 인가 뒤에만 presigned URL 을
 * 발급한다 — "로그인 전원 열람"이 아니라 보고서/알림과 같은 입자의 접근 통제다.
 */
@Service
@RequiredArgsConstructor
public class DefectQueryService {

    private final DefectRepository defectRepository;
    private final InspectionAssetPort assetPort;
    private final InspectionStoragePort storagePort;

    /**
     * 블레이드의 결함 이미지 목록(최신 적재 순). 미담당/미존재 블레이드는 404 로 은닉된다(가드 규약).
     * ADMIN 은 가드를 통과하므로 미존재를 따로 404 로 판정한다(명세: blade id not found).
     */
    @Transactional(readOnly = true)
    public List<DefectImageResult> getDefectImages(Long userId, boolean admin, Long bladeId) {
        assetPort.checkBladeAccess(userId, admin, bladeId); // 비-ADMIN 미담당/미존재 → 404 은닉
        if (admin && !assetPort.bladeExists(bladeId)) {
            throw new BusinessException(ErrorCode.BLADE_NOT_FOUND);
        }

        // 이미지(image_path) 단위로 그룹핑한다. 조회 순서(최신 적재 순)를 유지하려고 LinkedHashMap 을 쓴다.
        Map<String, List<Defect>> byImage = new LinkedHashMap<>();
        for (Defect defect : defectRepository.findByBladeId(bladeId)) {
            byImage.computeIfAbsent(defect.getImagePath(), key -> new ArrayList<>()).add(defect);
        }

        List<DefectImageResult> results = new ArrayList<>(byImage.size());
        for (Map.Entry<String, List<Defect>> entry : byImage.entrySet()) {
            List<Defect> defects = entry.getValue();
            Defect first = defects.getFirst();
            results.add(new DefectImageResult(
                    entry.getKey(),
                    entry.getKey() == null ? null : storagePort.presignImageView(entry.getKey()),
                    defects.stream()
                            .map(d -> new DefectImageResult.DefectItem(d.getId(), d.getDefectType(), d.getSeverity(),
                                    d.getBboxX(), d.getBboxY(), d.getBboxW(), d.getBboxH(), d.getConfidence()))
                            .toList(),
                    defects.stream().map(Defect::getSeverity).filter(Objects::nonNull)
                            .max(Comparator.naturalOrder()).orElse(null),
                    first.getPartSide(),
                    first.getCreatedAt()));
        }
        return results;
    }
}
