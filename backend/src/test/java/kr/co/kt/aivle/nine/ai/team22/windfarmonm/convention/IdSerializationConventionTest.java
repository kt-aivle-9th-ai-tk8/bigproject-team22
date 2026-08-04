package kr.co.kt.aivle.nine.ai.team22.windfarmonm.convention;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

/**
 * FE 계약: DTO(요청·응답)의 스칼라 식별자 필드(id, ~Id)는 반드시 String 으로 주고받는다.
 * <p>
 * JavaScript Number(IEEE-754 double)는 정수를 2^53-1 까지만 정확히 표현하므로, 64비트 id 를
 * 숫자로 직렬화하면 FE 가 JSON.parse 시 '조용히' 다른 값으로 손상될 수 있다. 이를 타입 계약으로
 * 강제해, 누가 실수로 id 를 Long/Integer 로 두어도 이 테스트(=CI)가 즉시 실패하도록 한다.
 * <p>
 * - 대상: presentation.dto 의 모든 DTO(요청 *Request / 응답 *Response). application.dto 의 내부
 *   Result 는 대상 아님(직렬화되지 않고 도메인 id(Long)를 그대로 사용).
 * - 컬렉션 식별자(~Ids, 예: List&lt;String&gt;)는 raw type 이 List 라 이 규칙(haveRawType=String)에서
 *   제외되며 코드리뷰로 관리한다.
 * - 경로변수({id})는 필드가 아니라 메서드 파라미터라 이 규칙 대상이 아니며, String + ApiIds.toLong
 *   변환 관례로 관리한다.
 */
@AnalyzeClasses(packages = "kr.co.kt.aivle.nine.ai.team22.windfarmonm")
class IdSerializationConventionTest {

    @ArchTest
    static final ArchRule scalar_id_fields_in_dto_must_be_string =
            fields()
                    .that().areDeclaredInClassesThat().resideInAPackage("..presentation.dto..")
                    .and().haveNameMatching("id|.*Id")
                    .should().haveRawType(String.class)
                    .because("FE 계약: 요청·응답 DTO의 스칼라 식별자는 String 으로 주고받는다 (JS 2^53 조용한 손실 방지)");
}
