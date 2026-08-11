package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.PartSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 키 규약의 조립·해석이 서로 왕복 가능함을 고정한다 — 발급과 목록 검증이 어긋나면
 * 업로드는 됐는데 추론 대상에서 빠지는 조용한 유실이 생긴다.
 */
class InspectionObjectKeysTest {

    @Test
    @DisplayName("키 형식: {prefix}/inspections/{id}/{bladeId}/{side}/{seq}.jpg")
    void imageKey_format() {
        assertThat(InspectionObjectKeys.imageKey("content", 12, 34, PartSide.LE, 5))
                .isEqualTo("content/inspections/12/34/LE/5.jpg");
    }

    @Test
    @DisplayName("점검 프리픽스는 '/' 로 끝나 다른 점검 id 와의 접두 충돌을 막는다(1 vs 12)")
    void inspectionPrefix_endsWithSlash() {
        assertThat(InspectionObjectKeys.inspectionPrefix("content", 1))
                .isEqualTo("content/inspections/1/");
    }

    @Test
    @DisplayName("조립한 키는 해석으로 왕복된다")
    void parse_roundTrip() {
        String key = InspectionObjectKeys.imageKey("content", 12, 34, PartSide.SS, 2);

        InspectionObjectKeys.ParsedImage parsed = InspectionObjectKeys.parse(key).orElseThrow();

        assertThat(parsed.bladeId()).isEqualTo(34);
        assertThat(parsed.partSide()).isEqualTo(PartSide.SS);
    }

    @Test
    @DisplayName("규약 밖 키는 empty(수동 업로드 등은 건너뛴다)")
    void parse_invalidKeys() {
        assertThat(InspectionObjectKeys.parse("content/other/1.jpg")).isEmpty();
        assertThat(InspectionObjectKeys.parse("content/inspections/12/notANumber/LE/1.jpg")).isEmpty();
        assertThat(InspectionObjectKeys.parse("content/inspections/12/34/XX/1.jpg")).isEmpty();
        assertThat(InspectionObjectKeys.parse("content/inspections/12/34/LE/1/extra.jpg")).isEmpty();
    }
}
