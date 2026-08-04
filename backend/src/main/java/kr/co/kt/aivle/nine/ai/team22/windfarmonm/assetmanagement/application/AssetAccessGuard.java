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

    /**
     * 터빈 id 만으로 접근 권한을 검사한다(터빈 조회 전에 호출).
     * 존재 확인보다 먼저 수행해야 미담당 사용자에게 404/403 차이로 터빈 존재가 노출되지 않는다
     * — 미존재/미담당 모두 비-ADMIN 에게는 동일하게 {@link ErrorCode#TURBINE_ACCESS_DENIED}.
     */
    @Transactional(readOnly = true)
    public void checkTurbineAccessById(Long userId, boolean admin, Long turbineId) {
        if (admin) {
            return;
        }
        if (!assignmentRepository.existsByUserIdAndTurbineId(userId, turbineId)) {
            throw new BusinessException(ErrorCode.TURBINE_ACCESS_DENIED);
        }
    }

    /**
     * 블레이드 id 만으로 접근 권한을 검사한다(블레이드 조회 전에 호출).
     * 블레이드→터빈→단지→담당 배정을 단일 질의로 판정하며, 미존재/미담당 모두 비-ADMIN 에게는
     * 동일하게 {@link ErrorCode#BLADE_ACCESS_DENIED} 로 응답해 블레이드 존재를 노출하지 않는다.
     * <p>
     * 현재 블레이드는 터빈 상세 응답에 중첩되어 나가므로 터빈 가드로 충분하지만, 결함/점검 기능이
     * 블레이드 단위로 접근하게 되면 이 가드를 반드시 거치도록 한다(결함 이미지 인가 누락 재발 방지).
     */
    @Transactional(readOnly = true)
    public void checkBladeAccessById(Long userId, boolean admin, Long bladeId) {
        if (admin) {
            return;
        }
        if (!assignmentRepository.existsByUserIdAndBladeId(userId, bladeId)) {
            throw new BusinessException(ErrorCode.BLADE_ACCESS_DENIED);
        }
    }
}
