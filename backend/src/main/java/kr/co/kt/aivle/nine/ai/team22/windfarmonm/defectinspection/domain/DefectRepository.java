package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

import java.util.List;

/**
 * Defect 저장소 포트.
 */
public interface DefectRepository {

    List<Defect> saveAll(List<Defect> defects);

    /** 블레이드의 결함 전부(최신 적재 순). 이미지 단위 그룹핑은 호출측이 한다. */
    List<Defect> findByBladeId(Long bladeId);
}
