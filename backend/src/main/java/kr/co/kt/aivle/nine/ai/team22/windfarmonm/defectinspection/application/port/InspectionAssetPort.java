package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port;

import java.util.List;

/**
 * 점검이 필요로 하는 자산(assetmanagement) 접근 포트. 소비자(defectinspection)가 소유한다.
 */
public interface InspectionAssetPort {

    /**
     * 단지 접근 권한 검사. 미담당/미존재 모두 비-ADMIN 에게는 동일하게 404 로 응답한다(존재 은닉 —
     * {@code AssetAccessGuard.checkWindFarmAccess} 규약 그대로).
     */
    void checkWindFarmAccess(Long userId, boolean admin, Long windFarmId);

    /**
     * 터빈 접근 권한 검사. 미담당/미존재 모두 비-ADMIN 에게는 동일하게 404 로 응답한다(존재 은닉 —
     * {@code AssetAccessGuard.checkTurbineAccessById} 규약 그대로).
     */
    void checkTurbineAccess(Long userId, boolean admin, Long turbineId);

    /**
     * 터빈의 소속 단지 id. <b>인가 없는</b> 내부 조회(호출측 인가 선행).
     *
     * @return 소속 단지 id, 터빈이 없으면 null
     */
    Long windFarmIdOf(Long turbineId);

    /** 터빈의 블레이드 목록(태그→id 해석용). <b>인가 없는</b> 내부 조회(호출측 인가 선행). */
    List<BladeRef> bladesOf(Long turbineId);

    /**
     * 블레이드 접근 권한 검사. 미담당/미존재 모두 비-ADMIN 에게는 동일하게 404 로 응답한다(존재 은닉 —
     * {@code AssetAccessGuard.checkBladeAccessById} 규약 그대로).
     */
    void checkBladeAccess(Long userId, boolean admin, Long bladeId);

    /** 블레이드 존재 여부. ADMIN 은 가드를 통과하므로 미존재 404 판정에 따로 쓴다. */
    boolean bladeExists(Long bladeId);

    /** 블레이드 참조(id + 태그 A/B/C). */
    record BladeRef(Long id, String tag) {
    }
}
