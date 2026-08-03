package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.AssignmentRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 담당(Assignment) 기반 접근 통제. ADMIN 은 전체 단지를 열람할 수 있으므로 검사를 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class AssetAccessGuard {

    private final AssignmentRepository assignmentRepository;

    /**
     * 사용자가 열람 가능한(viewable/accessible) 단지 id 목록.
     * ADMIN 은 전체 열람이 가능하므로 {@code null}(= 제한 없음, "전체")을 반환한다.
     * 그 외 사용자는 담당(Assignment) 단지 id 목록을 반환한다(없으면 빈 목록).
     * "열람 범위"가 필요한 유스케이스는 이 메서드를 재사용해 admin=null(전체)/일반=목록 규약을 공유한다.
     */
    @Transactional(readOnly = true)
    public List<Long> viewableWindFarmIds(Long userId, boolean admin) {
        if (admin) {
            return null;
        }
        return assignmentRepository.findWindFarmIdsByUserId(userId);
    }

    /** 단지 접근 권한 검사. 권한 없으면 {@link ErrorCode#WIND_FARM_ACCESS_DENIED}. */
    @Transactional(readOnly = true)
    public void checkWindFarmAccess(Long userId, boolean admin, Long windFarmId) {
        if (admin) {
            return;
        }
        if (!assignmentRepository.existsByUserIdAndWindFarmId(userId, windFarmId)) {
            throw new BusinessException(ErrorCode.WIND_FARM_ACCESS_DENIED);
        }
    }

    /** 터빈 접근 권한 검사(터빈이 속한 단지 담당 여부). 권한 없으면 {@link ErrorCode#TURBINE_ACCESS_DENIED}. */
    @Transactional(readOnly = true)
    public void checkTurbineAccess(Long userId, boolean admin, Long windFarmId) {
        if (admin) {
            return;
        }
        if (!assignmentRepository.existsByUserIdAndWindFarmId(userId, windFarmId)) {
            throw new BusinessException(ErrorCode.TURBINE_ACCESS_DENIED);
        }
    }
}
