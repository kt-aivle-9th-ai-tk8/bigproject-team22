package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 결함(DL 추론 결과 1건 = 이미지 속 박스 1개). 점검·블레이드는 id 값으로만 참조한다.
 * <p>
 * severity 는 CNN 분류(1~4)를 정수로 담는다 — 모델이 내려주는 클래스명이 숫자가 아니면 null 로 두고
 * 로그로 남긴다(값 범위 검증은 애플리케이션 담당 — V5 주석).
 */
@Entity
@Table(name = "defect")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Defect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "defect_id")
    private Long id;

    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    @Column(name = "blade_id", nullable = false)
    private Long bladeId;

    /** YOLO 클래스명(9종: Contamination, Paint Damage 등). */
    @Column(name = "defect_type", nullable = false, length = 50)
    private String defectType;

    @Column(name = "severity")
    private Integer severity;

    /** 촬영 부위(LE/TE/PS/SS). 키 규약에서 해석해 온다. */
    @Column(name = "part_side", length = 20)
    private String partSide;

    @Column(name = "bbox_x")
    private Double bboxX;

    @Column(name = "bbox_y")
    private Double bboxY;

    @Column(name = "bbox_w")
    private Double bboxW;

    @Column(name = "bbox_h")
    private Double bboxH;

    /** AI 검출 신뢰도 0~1. */
    @Column(name = "confidence")
    private Double confidence;

    /** 원본 이미지의 S3 키. 조회 시 presigned URL 발급의 근거가 된다(응답에 키 자체는 내보내지 않는다). */
    @Column(name = "image_path", length = 500)
    private String imagePath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private Defect(Long inspectionId, Long bladeId, String defectType, Integer severity, String partSide,
                   Double bboxX, Double bboxY, Double bboxW, Double bboxH, Double confidence, String imagePath) {
        this.inspectionId = inspectionId;
        this.bladeId = bladeId;
        this.defectType = defectType;
        this.severity = severity;
        this.partSide = partSide;
        this.bboxX = bboxX;
        this.bboxY = bboxY;
        this.bboxW = bboxW;
        this.bboxH = bboxH;
        this.confidence = confidence;
        this.imagePath = imagePath;
    }

    /** 추론 결과에서 결함 1건을 적재한다. */
    public static Defect detected(Long inspectionId, Long bladeId, String defectType, Integer severity,
                                  PartSide partSide, Double bboxX, Double bboxY, Double bboxW, Double bboxH,
                                  Double confidence, String imagePath) {
        return new Defect(inspectionId, bladeId, defectType, severity,
                partSide == null ? null : partSide.name(), bboxX, bboxY, bboxW, bboxH, confidence, imagePath);
    }
}
