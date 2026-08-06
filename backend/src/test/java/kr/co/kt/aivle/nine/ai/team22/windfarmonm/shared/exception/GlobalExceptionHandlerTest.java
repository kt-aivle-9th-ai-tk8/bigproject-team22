package kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.AdminRoleInterceptor;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginCheckInterceptor;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMemberArgumentResolver;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config.WebConfig;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.health.HealthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 전역 예외 처리 회귀 테스트.
 * <p>
 * 핵심 회귀 지점: {@code @ExceptionHandler(Exception.class)} catch-all 이 Spring MVC 표준 예외까지 삼키면
 * 405/415 같은 요청 오류가 전부 500 으로 나간다(그리고 ERROR 로그까지 쌓인다). 공개 경로인 /health 로
 * 인증 영향 없이 이를 검증한다.
 */
@WebMvcTest(HealthController.class)
@Import({WebConfig.class, LoginCheckInterceptor.class, AdminRoleInterceptor.class, LoginMemberArgumentResolver.class})
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("GET 전용 경로에 POST 하면 405 를 반환한다(500 아님)")
    void methodNotSupported_returns405() throws Exception {
        MvcResult result = mockMvc.perform(post("/health"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("\"success\":false")
                .doesNotContain("C999"); // 내부 오류 코드로 둔갑하지 않아야 한다
    }

    @Test
    @DisplayName("실패 응답은 success/code/message/data 한 가지 스키마로 나간다")
    void errorBody_usesSingleSchema() throws Exception {
        String body = mockMvc.perform(post("/health"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("\"success\"")
                .contains("\"code\"")
                .contains("\"message\"")
                .contains("\"data\"");
    }
}
