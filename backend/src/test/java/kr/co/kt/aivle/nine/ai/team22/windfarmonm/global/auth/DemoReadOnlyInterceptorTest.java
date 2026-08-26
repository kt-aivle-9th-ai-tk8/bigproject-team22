package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 데모 읽기 전용 보장: 세션 주체가 {@code Role.DEMO} 면 안전 메서드(GET/HEAD/OPTIONS)만 통과하고
 * 상태를 바꾸는 요청(POST/PUT/PATCH/DELETE)은 403 으로 막힌다. DEMO 가 아닌 주체는 이 인터셉터가
 * 관여하지 않는다(인증·쓰기 허용은 별도 인터셉터/서비스의 몫).
 */
class DemoReadOnlyInterceptorTest {

    private final DemoReadOnlyInterceptor interceptor = new DemoReadOnlyInterceptor();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /** role 이 null 이면 세션 없음, 그 외엔 해당 role 의 로그인 주체가 담긴 세션을 만든다. */
    private MockHttpServletRequest request(String method, Role role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        if (role != null) {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(SessionConst.LOGIN_MEMBER, new LoginMember(1L, "a123456", "데모", role));
            request.setSession(session);
        }
        return request;
    }

    @Nested
    @DisplayName("데모(role=DEMO) 세션")
    class DemoSession {

        @Test
        @DisplayName("쓰기(POST/PUT/PATCH/DELETE)는 403(DEMO_READ_ONLY)으로 막힌다")
        void blocksWrites() {
            for (String method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
                assertThatThrownBy(() -> interceptor.preHandle(request(method, Role.DEMO), response, new Object()))
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DEMO_READ_ONLY);
            }
        }

        @Test
        @DisplayName("조회(GET/HEAD/OPTIONS)는 통과한다")
        void allowsReads() {
            for (String method : new String[]{"GET", "HEAD", "OPTIONS"}) {
                assertThat(interceptor.preHandle(request(method, Role.DEMO), response, new Object())).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("데모가 아닌 요청")
    class NonDemo {

        @Test
        @DisplayName("일반 로그인(MANAGER) 세션의 쓰기는 이 인터셉터가 막지 않는다")
        void doesNotBlockNormalWrites() {
            assertThat(interceptor.preHandle(request("POST", Role.MANAGER), response, new Object())).isTrue();
        }

        @Test
        @DisplayName("세션이 없는 쓰기도 이 인터셉터는 통과시킨다(인증은 별도 인터셉터가 처리)")
        void doesNotBlockWhenNoSession() {
            assertThat(interceptor.preHandle(request("DELETE", null), response, new Object())).isTrue();
        }
    }
}
