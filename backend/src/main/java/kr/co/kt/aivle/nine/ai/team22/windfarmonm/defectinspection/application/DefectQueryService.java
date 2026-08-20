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
import java.util.Set;
import java.util.stream.Collectors;

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
        List<Defect> found = defectRepository.findByBladeId(bladeId);
        Map<String, List<Defect>> byImage = new LinkedHashMap<>();
        for (Defect defect : found) {
            byImage.computeIfAbsent(defect.getImagePath(), key -> new ArrayList<>()).add(defect);
        }

        // 어떤 원본에 썸네일이 있는지 점검 프리픽스 LIST 로 한 번에 판정한다(장마다 존재를 묻지 않는다).
        Set<Long> inspectionIds = found.stream()
                .map(Defect::getInspectionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> thumbnailKeys = inspectionIds.isEmpty()
                ? Map.of()
                : storagePort.findThumbnailKeys(inspectionIds);

        List<DefectImageResult> results = new ArrayList<>(byImage.size());
        for (Map.Entry<String, List<Defect>> entry : byImage.entrySet()) {
            List<Defect> defects = entry.getValue();
            Defect first = defects.getFirst();
            // 썸네일은 원본을 줄인 것이 아니라 별도 경로({partSide}/thumb/{seq}.jpg)의 다른 객체다.
            // 아직 만들어지지 않았으면(생성 지연·실패) 원본 URL 로 폴백한다 — 화면이 깨지는 것보다 낫다.
            String imageKey = entry.getKey();
            String imageUrl = imageKey == null ? null : storagePort.presignImageView(imageKey);
            String thumbnailKey = imageKey == null ? null : thumbnailKeys.get(imageKey);
            String thumbnailUrl = thumbnailKey == null ? imageUrl : storagePort.presignImageView(thumbnailKey);
            results.add(new DefectImageResult(
                    imageKey,
                    imageUrl,
                    thumbnailUrl,
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
