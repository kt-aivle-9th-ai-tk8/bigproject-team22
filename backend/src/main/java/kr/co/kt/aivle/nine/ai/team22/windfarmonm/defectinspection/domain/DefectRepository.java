package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

import java.util.List;

/**
 * Defect 저장소 포트. 조회(결함 이미지 목록)는 P5 조회 API 에서 확장한다.
 */
public interface DefectRepository {

    List<Defect> saveAll(List<Defect> defects);
}
