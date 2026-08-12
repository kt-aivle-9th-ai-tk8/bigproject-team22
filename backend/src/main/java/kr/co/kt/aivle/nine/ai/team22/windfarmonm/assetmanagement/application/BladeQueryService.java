package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.BladeIdentity;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Blade;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.BladeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 블레이드 조회 유스케이스. 타 BC 가 {@code BladeRepository} 를 직접 잡지 않도록(규약 위반)
 * application 서비스로 감싼다.
 */
@Service
@RequiredArgsConstructor
public class BladeQueryService {

    private final BladeRepository bladeRepository;

    /**
     * 터빈의 블레이드 식별 정보(id·태그) 목록. <b>인가를 하지 않는</b> 내부 조회이므로
     * 호출측이 담당 인가를 이미 통과한 맥락에서만 쓸 것(예: 점검 생성 시 태그→id 해석).
     */
    @Transactional(readOnly = true)
    public List<BladeIdentity> getBladeIdentities(Long turbineId) {
        return bladeRepository.findByTurbineId(turbineId).stream()
                .map(blade -> new BladeIdentity(blade.getId(), blade.getTag()))
                .toList();
    }
}
