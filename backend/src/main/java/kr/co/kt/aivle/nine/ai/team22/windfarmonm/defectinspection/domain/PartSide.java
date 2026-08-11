package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

/**
 * 블레이드 촬영 부위. FE 업로더의 표면 선택지와 defect.part_side 컬럼(V5: 'LE / TE / PS / SS')에 대응한다.
 */
public enum PartSide {

    /** Leading Edge(전연) */
    LE,

    /** Trailing Edge(후연) */
    TE,

    /** Pressure Side(압력면) */
    PS,

    /** Suction Side(흡입면) */
    SS
}
