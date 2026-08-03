package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.AssignmentRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 담당(Assignment) 기반 접근 통제. ADMIN 은 전체 단지를 열람할 수 있으므로 검사를 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class AssetAccessGuard {

    private final AssignmentRepository assignmentRepository;

    /** 사용자가 담당하는 단지 id 목록(ADMIN 은 null 을 반환하여 "전체" 의미로 사용) */
    @Transactional(readOnly = true)
    public java.util.List<Long> assignedWindFarmIds(Long userId) {
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
