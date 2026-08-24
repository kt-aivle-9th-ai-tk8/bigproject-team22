package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.security;

/**
 * 응답에 실리는 개인정보(이름·사번 등)를 가리는 유틸.
 * <p>
 * 마스킹은 <b>반드시 서버에서</b> 해야 한다. FE 에서 가려도 사용자는 개발자도구로 응답 원문을 보거나
 * 자기 인증서를 심은 프록시로 자기 TLS 트래픽을 복호화할 수 있다 — 이 위협모델에서는 로그인한
 * 사용자 자신이 열람 주체라 클라이언트 측 통제가 원리적으로 성립하지 않는다.
 * <p>
 * 규칙: 문자열을 3등분해 <b>가운데를 마스킹</b>하되, 마스킹 길이에 올림을 적용해 짧은 값도 예외 없이
 * 가려지도록 한다(1~2글자도 대상). 남는 자리는 앞쪽을 우선한다 — 이름은 성이, 사번은 식별력이 낮은
 * 앞자리가 남고 개인을 특정하는 뒷자리가 가려진다.
 * <pre>
 *   mid  = ceil(n / 3)           1→1  2→1  3→1  4→2  7→3  11→4
 *   head = ceil((n - mid) / 2)
 *   tail = n - mid - head
 *
 *   김           → *
 *   이든         → 이*
 *   홍길동       → 홍*동
 *   남궁민수     → 남**수
 *   2401001      → 24***01
 *   01012345678  → 0101****678
 * </pre>
 */
public final class PiiMasker {

    private static final String MASK = "*";

    private PiiMasker() {
    }

    /**
     * 개인정보 문자열을 마스킹한다. {@code null}·빈 문자열은 그대로 통과시킨다
     * (가릴 것이 없고, 호출부에서 null 분기를 하지 않아도 되게 한다).
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // 서로게이트 페어(이모지 등)가 섞여도 글자 단위로 자르도록 코드포인트로 다룬다.
        int[] codePoints = value.codePoints().toArray();
        int length = codePoints.length;

        int mid = ceilDiv(length, 3);
        int head = ceilDiv(length - mid, 2);
        int tail = length - mid - head;

        return new String(codePoints, 0, head)
                + MASK.repeat(mid)
                + new String(codePoints, length - tail, tail);
    }

    private static int ceilDiv(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}
